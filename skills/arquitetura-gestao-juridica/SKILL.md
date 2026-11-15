---
name: arquitetura-gestao-juridica
description: Aplica a arquitetura do Sistema de Gestão Jurídica SaaS (TCC ADS IFBA) ao escrever, revisar ou planejar código do projeto. Monólito modular + DDD + Hexagonal em Java 25 / Spring Boot / Maven, multi-tenancy com tenant_id + Hibernate @TenantId + Row-Level Security no PostgreSQL, fronteiras de módulos verificadas com Spring Modulith, eventos de domínio, auditoria, portabilidade de dados e testes com Testcontainers. Use ao criar ou alterar módulos, entidades, casos de uso, controllers, migrações Flyway, eventos, permissões ou testes deste projeto, ou ao verificar se um código respeita a arquitetura.
metadata:
    version: "1.0.0"
---

# Arquitetura do Sistema de Gestão Jurídica SaaS

Você implementa e revisa código de um **SaaS multi-tenant para escritórios de advocacia**, desenvolvido como MVP de TCC. A fonte da verdade das decisões é o `README.md` do projeto (`tcc-ads-ifba/README.md`). Se o README e esta skill divergirem, siga o README e avise o usuário.

## Stack

| Item | Tecnologia |
|---|---|
| Linguagem | Java 25 (LTS) |
| Build | Maven 3.10.0 |
| IDE | IntelliJ IDEA |
| Framework | Spring Boot 4.1.2, Spring Data JPA / Hibernate 7.4, Spring Modulith 2.1.1, Spring Security 7.1 + JWT (OAuth2 Resource Server), SpringDoc OpenAPI 3.2.0, Spring Boot Actuator |
| Banco | PostgreSQL + Flyway |
| Testes | JUnit 6, Testcontainers 2, Spring Modulith Test / ArchUnit |
| Containers | Docker |
| Frontend | a definir |

Itens marcados como "a definir" no README (framework do frontend, emissor do JWT) **não devem ser escolhidos por conta própria**: pergunte ao usuário ou registre a premissa.

## Regras invioláveis

1. **Isolamento entre escritórios é o diferencial do MVP.** Nenhuma alteração pode permitir que um usuário acesse dados de outro escritório.
2. **Toda tabela de negócio tem `tenant_id`**, mapeado com Hibernate `@TenantId`, e uma **política de RLS** criada na mesma migração Flyway.
3. **O `tenant_id` vem sempre do token JWT** do usuário autenticado. Nunca aceite tenant por parâmetro, path, header ou corpo da requisição, nem em DTOs de entrada.
4. **Cada módulo é dono das suas tabelas.** Proibido JOIN, `@ManyToOne` ou repositório apontando para tabela de outro módulo; referencie outro agregado apenas pelo ID.
5. **Um módulo só acessa outro pela API pública** (tipos no pacote raiz do módulo ou pacotes marcados com `@NamedInterface`). Nunca importe `dominio`, `aplicacao` ou `adaptador` de outro módulo.
6. **Efeitos colaterais entre módulos via eventos de domínio** (notificação, auditoria). Chamada síncrona só quando a resposta é necessária na hora.
7. **O schema é criado só pelo Flyway.** `spring.jpa.hibernate.ddl-auto` deve ser `validate` (ou `none`), nunca `update`/`create`.
8. **Controllers são adaptadores de entrada dentro do módulo** (`adaptador/entrada/web`). Não existe camada "API/BFF" separada.
9. **Integrações externas** (e-mail, tribunais, storage) são adaptadores de saída atrás de uma porta, nunca chamadas diretamente pelo domínio.
10. **Backup do sistema = dados do escritório** (`tenant_id`), implementado no módulo `portabilidade` junto com a exportação. Backup do banco inteiro é tarefa de infraestrutura, não código de negócio.

## Módulos

| Módulo | Responsabilidade | Abordagem |
|---|---|---|
| `compartilhado` | Tipos base, exceções, infraestrutura comum (contexto de tenant) | — |
| `identidade` | Cadastro, login, emissão/validação de credenciais | Simplificada |
| `escritorio` | Escritórios (tenants) | DDD completo |
| `autorizacao` | Grupos, permissões, controle de acesso | DDD completo |
| `cliente` | Clientes | Simplificada (CRUD) |
| `processo` | Processos, partes, movimentações, prazos, audiências | DDD completo |
| `atividade` | Atividades, delegação, equipes, status, comentários | DDD completo |
| `financeiro` | Honorários, receitas, despesas, contas, pagamentos, contratos | DDD completo |
| `documento` | Documentos | Simplificada |
| `notificacao` | Notificações aos usuários (consome eventos) | Simplificada |
| `auditoria` | Registro de auditoria em `registro_auditoria` (consome eventos) | Simplificada |
| `portabilidade` | Backup e exportação dos dados do escritório | Simplificada |

Pacote raiz: `br.com.juridora` (repositório `tcc-backend-ads-ifba`, classe principal `JuridoraApiApplication`). Cada subpacote direto do pacote raiz é um módulo do Spring Modulith. Módulos e pacotes ficam em português, minúsculas e sem acentos (`processo`, `adaptador.entrada.web`).

## Fluxo de trabalho

### Ao criar uma funcionalidade
1. Identifique o **módulo dono** da funcionalidade. Se ela tocar dados de dois módulos, decida quem é o dono e como o outro participa (API pública ou evento).
2. Verifique a **abordagem do módulo** (DDD completo ou simplificada) e siga a estrutura de `references/estrutura-modulos.md`.
3. Se houver tabela nova ou alterada, crie a **migração Flyway com `tenant_id`, índice e RLS** (`references/multi-tenancy.md`).
4. Proteja o endpoint com a **permissão** correspondente (`references/multi-tenancy.md`, seção Autorização).
5. Publique **eventos de domínio** para efeitos colaterais e auditoria (`references/comunicacao-eventos.md`).
6. Escreva os **testes**: unitário da regra, integração, autorização e isolamento (`references/testes.md`).
7. Confira o **checklist** abaixo antes de concluir.

### Ao revisar código
Percorra o checklist e aponte cada violação com arquivo e linha, explicando qual regra foi violada e como corrigir.

### Ao planejar escopo
Respeite a classificação do README: funcionalidades **obrigatórias** antes das **desejáveis**. Se o usuário pedir algo desejável enquanto há obrigatórias pendentes, implemente, mas avise.

## Checklist

- [ ] Tabela nova tem `tenant_id NOT NULL`, índice começando por `tenant_id` e política de RLS (`ENABLE` + `FORCE`).
- [ ] Entidade tem campo `@TenantId`; nenhum DTO de entrada contém `tenantId`.
- [ ] Nenhum import de `..outromodulo.dominio..`, `..outromodulo.aplicacao..` ou `..outromodulo.adaptador..`.
- [ ] Nenhum relacionamento JPA ou consulta cruzando tabelas de outro módulo.
- [ ] Controller está em `adaptador/entrada/web`, chama um caso de uso (porta de entrada) e não um repositório.
- [ ] Domínio não depende de Spring Web, JPA de outro módulo ou clientes HTTP.
- [ ] Endpoint protegido por permissão; usuário sem permissão recebe 403.
- [ ] Recurso de outro escritório responde 404 (não revela existência).
- [ ] Operações relevantes publicam evento consumido pela auditoria.
- [ ] Testes de isolamento multi-tenant e de autorização cobrem o novo recurso.
- [ ] `ModularidadeTest` (`ApplicationModules.verify()`) continua passando.

## Referências

| Arquivo | Quando ler |
|---|---|
| `references/estrutura-modulos.md` | Criar módulo, pacote, entidade, caso de uso, controller ou adaptador |
| `references/multi-tenancy.md` | Tabela, migração, entidade, consulta, autenticação ou permissão |
| `references/comunicacao-eventos.md` | Integração entre módulos, eventos, notificação, auditoria, backup/exportação |
| `references/testes.md` | Escrever ou revisar testes |
