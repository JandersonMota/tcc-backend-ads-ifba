# CLAUDE.md

## Projeto
Sistema de Gestão Jurídica SaaS multi-tenant para escritórios de advocacia (MVP de TCC em Análise e Desenvolvimento de Sistemas). Visão, escopo e decisões arquiteturais estão no `README.md`.

## Ferramentas
- **Linguagem:** Java 25 (LTS)
- **Build:** Maven 3.10.0
- **IDE:** IntelliJ IDEA
- **Framework:** Spring Boot 4.1.2, Spring Data JPA / Hibernate 7.4, Spring Modulith 2.1.1, Spring Security 7.1 + JWT (OAuth2 Resource Server), SpringDoc OpenAPI 3.2.0, Spring Boot Actuator
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
- Spring Security / OAuth2 Resource Server estão **comentados no `pom.xml`** até o emissor do JWT ser definido; não escolha o emissor por conta própria.
- Infraestrutura de tenant pronta (HU03, ADR-012): `TenantContext` (`ScopedValue`, preenchido por adaptador de entrada), `@TenantId` via `EntidadeTenant` e `set_config('app.tenant_id')` no início de cada transação (`TenantJpaTransactionManager`). O filtro que lê o tenant do JWT entra com a HU06.
- HU05 (cadastro de escritório, ADR-013): `POST /api/autenticacao/cadastro` em `identidade`, que coordena `EscritorioApi` e `AutorizacaoApi` em uma transação; migrações `V3` (usuario) e `V4` (grupo); próxima migração é a `V5`. `autorizacao` nunca depende de `identidade`.
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
