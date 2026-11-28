# CLAUDE.md

## Projeto
Sistema de Gestão Jurídica SaaS multi-tenant para escritórios de advocacia (MVP de TCC em Análise e Desenvolvimento de Sistemas). Visão, escopo e decisões arquiteturais estão no `README.md`.

## Ferramentas
- **Linguagem:** Java 25 (LTS)
- **Build:** Maven 3.10.2
- **IDE:** IntelliJ IDEA
- **Framework:** Spring Boot 4.2.0, Spring Data JPA / Hibernate 7.4, Spring Modulith 2.2.0, Spring Security 7.2 + JWT (OAuth2 Resource Server), SpringDoc OpenAPI 3.2.1, Spring Boot Actuator
- **Banco de Dados:** PostgreSQL, com migrações via Flyway
- **Testes:** JUnit 6, Testcontainers 2, Spring Modulith Test / ArchUnit
- **Código do backend:** repositório `tcc-backend-ads-ifba`, pacote raiz `br.com.juridora`, classe principal `JuridoraApiApplication`
- **Containers:** Docker
- **Frontend:** a definir

## Regras de Arquitetura
- Monólito modular + DDD + Hexagonal (Ports and Adapters).
- **Multi-tenancy:** schema compartilhado com coluna `tenant_id` em toda tabela de negócio; filtro na aplicação (Hibernate `@TenantId` / `@Filter`) e Row-Level Security no PostgreSQL.
- O `tenant_id` vem **sempre do token JWT**, nunca de parâmetro, header ou corpo da requisição.
- Cada módulo é dono das suas tabelas: sem JOIN ou acesso direto a tabelas de outro módulo.
- Um módulo só acessa outro pela API pública; nunca pelo `dominio` ou `adaptador` interno.
- Efeitos colaterais entre módulos (notificação, auditoria) via eventos de domínio.
- Schema do banco criado apenas por migrações Flyway (incluindo políticas de RLS), nunca pelo Hibernate.
- DDD tático completo em `autorizacao`, `escritorio`, `atividade`, `processo`, `financeiro`; estrutura simplificada em `identidade`, `cliente`, `documento`, `notificacao`, `auditoria`, `portabilidade` e cadastros auxiliares (categorias).
- `compartilhado` é um módulo aberto (shared kernel, `@ApplicationModule(type = OPEN)`): pode ser usado por todos, mas não depende de nenhum módulo de negócio.
- `spring.jpa.hibernate.ddl-auto` é `validate`; tabelas de infraestrutura (ex.: `event_publication` do Spring Modulith) também vêm do Flyway.
- Pacotes e módulos (backend e frontend) em **português**, minúsculas e sem acentos (ex.: `processo`, `notificacao`, `adaptador.entrada.web`).

## Estado atual
- Segurança ligada (HU06, ADR-014): JWT RS256 emitido pela própria aplicação (`JwtEncoder`) no `POST /api/autenticacao/login` e validado pelo OAuth2 Resource Server; nega por padrão (`SegurancaConfiguracao` em `compartilhado.infraestrutura.seguranca`). Chaves em `JWT_CHAVE_PRIVADA`/`JWT_CHAVE_PUBLICA`; sem elas, par temporário (local e testes).
- Infraestrutura de tenant (HU03, ADR-012): `TenantContext` (`ScopedValue`, preenchido por adaptador de entrada), `@TenantId` via `EntidadeTenant` e `set_config('app.tenant_id')` no início de cada transação (`TenantJpaTransactionManager`). Nas requisições, o `TenantDoTokenFiltro` lê `tenant_id` e `sub` do JWT validado.
- HU05 (cadastro de escritório, ADR-013): `POST /api/autenticacao/cadastro` em `identidade`, que coordena `EscritorioApi` e `AutorizacaoApi` em uma transação; migrações `V3` (usuario) e `V4` (grupo). `autorizacao` nunca depende de `identidade`.
- HU06 (login, ADR-015): credencial buscada sem tenant só pela função `credencial_por_email` (`V5`, `SECURITY DEFINER`); permissões do grupo na claim `permissoes`; bloqueio em memória após 5 falhas (`ControleTentativasLogin`); `GET /api/perfil`.
- HU07 (logout, ADR-016): `POST /api/autenticacao/sair` grava o `jti` em `token_revogado` (`V6`, com RLS) até o token expirar; limpeza agendada pela função `remover_tokens_revogados_vencidos` (`SECURITY DEFINER`).
- HU08 (gestão de usuários): `/api/usuarios` (listar paginado, buscar, cadastrar com `grupoId`, editar, desativar, reativar) e `GET /api/grupos`, com `@PreAuthorize` em `USUARIO_VISUALIZAR`/`USUARIO_GERENCIAR`. Limite de usuários ativos do plano (RN09) via `EscritorioApi.limitesDoPlano()` (`juridora.planos`), com trava consultiva por tenant. O `TenantDoTokenFiltro` consulta a porta `SessoesAtivas` (implementada na `identidade`): recusa token revogado (HU07), de usuário desativado ou com `iat` anterior a `usuario.sessoes_validas_desde` (`V7`). Exceções HTTP em `compartilhado.excecao`: `AcessoNegadoException` 403, `RecursoNaoEncontradoException` 404, `RegraNegocioException` 422.
- HU09 (grupos e permissões): `/api/grupos` (detalhar, criar, renomear, `PUT /{id}/permissoes` substituindo o conjunto, excluir) e `GET /api/permissoes`, só para o proprietário via `hasRole('PROPRIETARIO')` (`ROLE_PROPRIETARIO` vem da claim `proprietario`, `Autoridades`). Grupos padrão e personalizados são renomeados e só saem sem usuários; o grupo Proprietário não é renomeado nem excluído e mantém todas as permissões (`GrupoProprietarioProtegidoException`) (porta `MembrosDoGrupo`, implementada pela `identidade`; travas `FOR UPDATE`/`FOR SHARE`). Nome único sem caixa (`V8`). Eventos `Grupo*Event` e `PermissoesDoGrupoAlteradasEvent` aguardam a auditoria (HU12). Próxima migração é a `V9`.
- HU10 (troca de grupo): `PUT /api/usuarios/{id}/grupo` com `USUARIO_GERENCIAR`; vale no próximo login. O grupo Proprietário é reservado ao dono (`AutorizacaoApi.ehGrupoProprietario`, `GrupoReservadoAoProprietarioException`): o proprietário não sai e ninguém mais entra, nem no cadastro da HU08. Ninguém troca o próprio grupo (`AutoTrocaDeGrupoException`). Evento `UsuarioGrupoAlteradoEvent` aguarda a HU12.
- HU11 (controle de acesso em toda rota): todo endpoint declara `@PreAuthorize` (`hasAuthority('RECURSO_ACAO')`, `hasRole('PROPRIETARIO')`, `isAuthenticated()` ou `permitAll()`); o `ControleDeAcessoTest` (ArchUnit) reprova endpoint sem regra ou com permissão fora do enum `Permissao`. Sem a permissão → 403 (inclusive para recurso de outro escritório); com ela, outro escritório → 404. O grupo Proprietário sempre tem o catálogo inteiro (`Grupo.getPermissoes`, RN04). Actuator: só `health` é público; o resto é `denyAll`. `ControleDeAcessoIT` cobre todas as rotas protegidas: ao criar rota nova, inclua-a na tabela `rotas()`.
- Nomes em português, inclusive endpoints (`/api/processos`) e sufixos de camada (`CasoDeUso`, `Repositorio`); só `Controller`, `Request`, `Response`, `Event`, `Exception` e `Api` ficam em inglês (`doc-mvp/07-codigo-e-desenvolvimento/padroes-de-codigo.md`).
- Detalhes de implementação (multi-tenancy, eventos, testes, estrutura de módulos): skill `.claude/skills/arquitetura-gestao-juridica`.

## CI
- GitHub Actions (`.github/workflows/ci.yml`) roda `./mvnw -B verify` com JDK 25 a cada push e PR; Dependabot em `.github/dependabot.yml`.
- Trabalho em branches (`feat/`, `fix/`, `chore/`...) com merge na `main` por pull request com CI verde (ver `doc-mvp/07-codigo-e-desenvolvimento/fluxo-de-trabalho-git.md`).

## Comandos
- `./mvnw verify` — compila e roda todos os testes (requer Docker para o Testcontainers).
- `./mvnw test -Dtest="ModularidadeTest,CamadasTest"` — verifica fronteiras entre módulos e camadas (não requer Docker).
- `./mvnw spring-boot:test-run` — sobe a aplicação com PostgreSQL via Testcontainers.
- `docker compose -f docker/docker-compose.yml up -d` + `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` — aplicação com o banco persistente `juridora-postgres` (Flyway como `app_owner`, aplicação como `app_user`, sem `BYPASSRLS`).
- `docker compose -f docker/docker-compose.yml --profile api up -d --build` — banco e API em contêineres (imagem do `Dockerfile` multi-stage, Temurin 25 JRE), API em `localhost:8080`.
