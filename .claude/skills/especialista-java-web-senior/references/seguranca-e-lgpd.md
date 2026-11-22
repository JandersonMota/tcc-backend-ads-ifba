# Segurança e LGPD

O Juridora guarda dados de clientes de escritórios de advocacia, protegidos por sigilo profissional (Estatuto da Advocacia, Lei 8.906/1994, art. 7º, II) e pela LGPD. Segurança aqui é requisito funcional. Este arquivo orienta decisões de engenharia e não substitui orientação jurídica: em dúvidas de interpretação legal, sinalize ao usuário.

## Sumário

1. Princípios
2. Autenticação com JWT (HU06)
3. Autorização
4. Entrada não confiável
5. Upload e download de documentos
6. Segredos e configuração
7. Logs, erros e auditoria
8. LGPD na prática
9. Dependências
10. Checklist de segurança do PR

## 1. Princípios

- **Defesa em profundidade.** O isolamento entre escritórios tem duas camadas (`@TenantId` e RLS), e as duas são testadas. Nenhuma mudança deve depender de uma só.
- **Falhar fechado.** Sem tenant, nenhuma linha (o RLS já se comporta assim); sem permissão, 403; na dúvida, negar.
- **Menor privilégio.** A aplicação conecta como `app_user`, só com DML e sem `BYPASSRLS`. Os endpoints do Actuator ficam restritos ao necessário.
- **Nunca confie no cliente.** Tenant, usuário, permissões e preços vêm do servidor (JWT validado, banco), nunca do corpo, da query ou do header.

## 2. Autenticação com JWT (HU06)

O emissor do JWT está "a definir": não escolha por conta própria. Quando ele for definido, o desenho esperado é este:

- **Resource Server:** `spring-boot-starter-security-oauth2-resource-server`, hoje comentado no `pom.xml`.
- **Validações do token:** assinatura, `exp`, `iss` (`spring.security.oauth2.resourceserver.jwt.issuer-uri` ou `jwk-set-uri`) e `aud` (`...jwt.audiences`). Token sem audiência conferida pode ter sido emitido para outro sistema.
- **Claims:**
  - `sub` é o usuário, e `tenant_id` é o escritório;
  - as permissões vêm em uma claim própria, convertida em authorities sem prefixo (`JwtGrantedAuthoritiesConverter` com `setAuthorityPrefix("")`), para que `hasAuthority('PROCESSO_CRIAR')` funcione.
- **Sessão e CSRF:**
  - API stateless (`SessionCreationPolicy.STATELESS`);
  - CSRF pode ser desligado só se o token trafegar no header `Authorization`;
  - com token em cookie, o CSRF volta a ser necessário.
- **Filtro do tenant:**
  - roda depois da autenticação;
  - lê `tenant_id` e `sub` do JWT já validado;
  - envolve o restante da cadeia com `TenantContext.executarComo`, porque o `ScopedValue` só vale dentro do escopo.
  - Como `chain.doFilter` lança exceções checadas, o `TenantContext` vai precisar de uma sobrecarga com `ScopedValue.CallableOp` (`java-25-moderno.md`, seção 5).
- **Senhas,** se o módulo `identidade` as armazenar: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. Ele grava o prefixo do algoritmo junto com o hash, o que permite migrar de algoritmo depois. Nunca use hash rápido (MD5, SHA-*).
- **Login:** limite tentativas por usuário e IP, e devolva a mesma mensagem para "usuário inexistente" e "senha errada".

## 3. Autorização

- **Negar por padrão:** `anyRequest().authenticated()`, com exceções explícitas (login, health).
- **`@PreAuthorize` por endpoint,** com as permissões da matriz do projeto (`RECURSO_ACAO`). A regra de 403 e 404 está na skill de arquitetura.
- **IDOR** (acesso a um recurso pelo ID de outro) é barrado pelo filtro de tenant entre escritórios. Dentro do mesmo escritório, a regra é de negócio: por exemplo, um estagiário só vê os próprios processos. Teste esses casos.
- **Handlers de erro:** `AccessDeniedException` e `AuthenticationException` precisam de handlers próprios no `@RestControllerAdvice` (`spring-boot-4.md`, seção 3).
- **Actuator:** exponha só `health` e `info` publicamente. O endpoint `modulith` revela a estrutura interna; restrinja-o em produção.

## 4. Entrada não confiável

- **Validação na borda** (Bean Validation nos `*Request`) e invariantes no domínio.
- **Mass assignment:** o `*Request` tem só os campos que o cliente pode definir. O `CamadasTest` já barra `tenantId`; aplique o mesmo raciocínio a `status`, `valor` e `responsavel` quando forem controlados pelo sistema.
- **SQL e JPQL** sempre com parâmetros (`:nome`, `?`). Nunca concatene entrada em consulta, nem em `ORDER BY`: a ordenação vem de uma lista permitida.
- **Limites:**
  - tamanho de página máximo;
  - tamanho de upload (`spring.servlet.multipart.max-file-size` e `max-request-size`);
  - tamanho de campos (`@Size`, igual ao da coluna).
- **Mensagens de validação** podem repetir a entrada do usuário: são JSON (sem XSS no backend), mas o frontend deve escapar ao exibir.

## 5. Upload e download de documentos

- **Tipo de arquivo:** não confie em `Content-Type` nem na extensão. Valide por assinatura de bytes (*magic numbers*) e por uma lista permitida (PDF, imagens, DOCX). Uma biblioteca de detecção, como o Apache Tika, é dependência nova: proponha antes.
- **Armazenamento:**
  - nome gerado (UUID), nunca o nome enviado;
  - o nome original vai só no banco, como metadado;
  - fora do diretório da aplicação;
  - normalize o caminho e confira que continua dentro da raiz (`caminho.normalize().startsWith(raiz)`), para barrar path traversal.
- **Download:**
  - sempre pelo ID e passando pelo filtro de tenant e pela permissão;
  - `Content-Disposition: attachment` com nome sanitizado;
  - `X-Content-Type-Options: nosniff`, que o Spring Security envia por padrão.
- **O armazenamento fica atrás de uma porta** (`adaptador/saida/armazenamento`), para trocar disco local por armazenamento de objetos sem tocar no domínio.

## 6. Segredos e configuração

- **Senhas, chaves e tokens** vêm de variáveis de ambiente ou do `docker/.env`, que não é versionado. Nunca ficam no `application.yaml` versionado, em teste, log ou commit.
- **Segredo commitado é segredo vazado.** Removê-lo do histórico não basta: troque-o.
- **Nos testes,** use as credenciais efêmeras do Testcontainers.
- **CORS:** quando o frontend for definido, libere só as origens dele, sem `*` com credenciais.

## 7. Logs, erros e auditoria

- **Nunca registre** tokens, senhas, cabeçalho `Authorization`, CPF/CNPJ, conteúdo de documentos, peças processuais ou dados de clientes. IDs (UUID) bastam para correlacionar.
- **Respostas de erro** sem stack trace, SQL ou nomes internos. O detalhe técnico vai só para o log, com o stack trace.
- **Correlação:** o `traceId` do Micrometer Tracing liga log, requisição e erro (`performance-e-operacao.md`, seção 7).
- **Auditoria** (quem fez o quê e quando) é responsabilidade do módulo `auditoria`, via eventos, e não do log técnico. Os registros são só de inserção.

## 8. LGPD na prática

- **Papéis:** o escritório é o **controlador** dos dados dos clientes dele, e o Juridora (SaaS) é o **operador**. Isso orienta contratos, avisos e o que o sistema permite fazer com os dados.
- **Bases legais típicas:** execução de contrato e exercício regular de direitos em processo judicial (LGPD art. 7º, VI, e art. 11, II, "d", para dados sensíveis). A confirmação jurídica cabe à orientação do TCC.
- **Minimização:** colete só o necessário para a funcionalidade. Campo "talvez útil" não entra.
- **Direitos do titular (art. 18):**
  - acesso e portabilidade são atendidos pelo módulo `portabilidade`;
  - correção, pelas telas de edição;
  - eliminação ou anonimização só quando não houver obrigação legal de guarda (art. 16).
  - Projete a anonimização (substituir dados pessoais mantendo os registros de processo e financeiro), em vez de `DELETE` em cascata.
- **Segurança (art. 46):**
  - TLS em trânsito;
  - criptografia em repouso (disco ou serviço gerenciado);
  - controle de acesso por permissão;
  - trilha de auditoria.
- **Incidentes (art. 48):** registros e auditoria precisam permitir responder o que vazou, de quem e quando.
- **Retenção:** defina, com o usuário, prazos por tipo de dado e o que acontece ao fim do contrato do escritório (exportar e depois excluir).

## 9. Dependências

- O Dependabot já abre PRs semanais. Trate atualizações de segurança como prioridade e rode `./mvnw -B verify` antes do merge.
- Antes de adicionar uma biblioteca, verifique se ela é mantida, se tem licença compatível e se o Spring já resolve o problema.
- Não declare versões que o Spring Boot já gerencia: o BOM recebe as correções de segurança.

## 10. Checklist de segurança do PR

- [ ] Nenhum dado de entrada define tenant, usuário, permissão ou campo controlado pelo sistema.
- [ ] Endpoint novo tem `@PreAuthorize` (a partir da HU06) e testes de 403 e 404.
- [ ] Consultas parametrizadas; ordenação e filtros vindos de lista permitida.
- [ ] Upload valida tipo e tamanho; download passa por tenant e permissão.
- [ ] Nada sensível em log, erro, exceção ou resposta.
- [ ] Nenhum segredo no código, YAML versionado ou teste.
- [ ] Dados pessoais novos: finalidade clara, minimização e caminho de exportação e anonimização.
