# tcc-backend-ads-ifba

[![CI](https://github.com/JandersonMota/tcc-backend-ads-ifba/actions/workflows/ci.yml/badge.svg)](https://github.com/JandersonMota/tcc-backend-ads-ifba/actions/workflows/ci.yml)

Backend do **Juridora**, sistema web multi-tenant de gestão jurídica para escritórios de advocacia (MVP do TCC – ADS/IFBA).

Arquitetura: **monólito modular** (Spring Modulith) + **DDD** + **Hexagonal** (Ports and Adapters). As decisões estão em [`tcc-ads-ifba/README.md`](../tcc-ads-ifba/README.md) e em [`doc-mvp/04-arquitetura-e-design-tecnico`](../tcc-ads-ifba/doc-mvp/04-arquitetura-e-design-tecnico/).

## Stack

| Item | Tecnologia |
|---|---|
| Linguagem / build | Java 25 (LTS) · Maven 3.10.2 (Maven Wrapper incluído) |
| Framework | Spring Boot 4.2.0 (Spring Framework 7.1) |
| Persistência | Spring Data JPA / Hibernate 7.4 · PostgreSQL · Flyway 12.11 |
| Modularidade | Spring Modulith 2.2.0 (core, JPA, actuator, observability) |
| Segurança | Spring Security 7.2 + JWT RS256 emitido pela aplicação (`JwtEncoder`) e validado pelo OAuth2 Resource Server (ADR-014) · Validation |
| API | Spring Web MVC · SpringDoc OpenAPI 3.2.1 (Swagger UI) |
| Observabilidade | Spring Boot Actuator |
| Testes | JUnit 6 · Testcontainers 2 (PostgreSQL) · Spring Modulith Test · ArchUnit 1.5.1 (via Spring Modulith) · JaCoCo |

Versões não declaradas no `pom.xml` são as gerenciadas pelo Spring Boot 4.2.0. H2 não é usado: o RLS só é testado no PostgreSQL real.

## Como executar

Pré-requisitos: JDK 25 e Docker em execução.

```bash
./mvnw spring-boot:test-run              # sobe a aplicação com PostgreSQL via Testcontainers
./mvnw verify                            # compila e executa todos os testes, unitários (*Test) e de integração (*IT); relatório JaCoCo em target/site/jacoco
./mvnw test -Dtest="ModularidadeTest,CamadasTest"  # só os testes de arquitetura (não precisa de Docker)
```

No Windows, use `mvnw.cmd`. No IntelliJ, execute `TestJuridoraApiApplication` (em `src/test/java`) para subir com o banco do Testcontainers, ou `JuridoraApiApplication` com o perfil `local` (abaixo).

### PostgreSQL local (Docker Compose)

Banco persistente, com nome fixo, para desenvolvimento e acesso pelo IntelliJ/pgAdmin:

```bash
docker compose -f docker/docker-compose.yml up -d          # sobe o contêiner juridora-postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local    # aplicação usando esse banco
docker compose -f docker/docker-compose.yml down           # para (mantém os dados)
docker compose -f docker/docker-compose.yml down -v        # para e apaga os dados
```

| Item | Valor padrão |
|---|---|
| Contêiner / imagem | `juridora-postgres` / `postgres:18.7` |
| Host / porta / banco | `localhost` / `5432` / `juridora` |
| Superusuário (só administração) | `postgres` / `postgres` |
| Flyway (dono das tabelas) | `app_owner` / `app_owner` |
| Aplicação (sem `BYPASSRLS`, só DML) | `app_user` / `app_user` |
| Volume de dados | `juridora-postgres-dados` |

Senhas, porta e perfil do Spring ficam em `docker/.env`, que **não é versionado** (está no `.gitignore`). Sem ele, valem os padrões da tabela acima. O IntelliJ pode carregá-lo na configuração de execução (EnvFile). Os usuários são criados por `docker/postgres/01-criar-usuarios.sh` apenas na primeira criação do volume. Depois de mudar o script ou as senhas, rode `down -v` para recriar o banco.

| Endereço | Conteúdo |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | Especificação OpenAPI |
| `http://localhost:8080/actuator/health` | Estado da aplicação |

Configuração completa do ambiente: [`configuracao-do-ambiente.md`](../tcc-ads-ifba/doc-mvp/07-codigo-e-desenvolvimento/configuracao-do-ambiente.md).

## Estrutura

```
tcc-backend-ads-ifba
├── .github
│   ├── workflows/ci.yml               ← CI: ./mvnw verify (JDK 25 + Testcontainers) a cada push e PR
│   └── dependabot.yml                 ← atualizações semanais (Maven, actions, docker-compose)
├── docker
│   ├── docker-compose.yml             ← contêiner juridora-postgres (PostgreSQL 18.7)
│   ├── .env                           ← senhas, porta e perfil local (não versionado)
│   └── postgres                       ← scripts de inicialização (usuários app_owner/app_user)
└── src
    ├── main
    │   ├── java/br/com/juridora
    │   │   ├── JuridoraApiApplication.java        ← classe principal (pacote raiz)
    │   │   ├── compartilhado          ← módulo aberto (shared kernel): tipos base, exceções, TenantContext, segurança
    │   │   │   ├── dominio
    │   │   │   ├── excecao
    │   │   │   └── infraestrutura
    │   │   │       ├── tenant
    │   │   │       ├── seguranca
    │   │   │       └── web
    │   │   ├── identidade             ← simplificado
    │   │   ├── escritorio             ← DDD completo
    │   │   ├── autorizacao            ← DDD completo
    │   │   ├── cliente                ← simplificado
    │   │   ├── processo               ← DDD completo
    │   │   ├── atividade              ← DDD completo
    │   │   ├── financeiro             ← DDD completo
    │   │   ├── documento              ← simplificado (+ adaptador/saida/armazenamento)
    │   │   ├── notificacao            ← simplificado (+ entrada/evento, saida/email)
    │   │   ├── auditoria              ← simplificado (+ entrada/evento)
    │   │   └── portabilidade          ← aplicacao + adaptador (sem domínio próprio)
    │   └── resources
    │       ├── application.yaml       ← configuração (perfis em application-{perfil}.yaml)
    │       ├── application-local.yaml ← perfil local: banco do docker-compose
    │       └── db/migration           ← migrações Flyway (V{n}__descricao.sql), com RLS; V1 = event_publication (Spring Modulith), V2 = escritorio
    └── test
        ├── java/br/com/juridora
        │   ├── TestcontainersConfiguration.java     ← PostgreSQL em contêiner; Flyway como app_owner, aplicação como app_user (sem BYPASSRLS)
        │   ├── TestJuridoraApiApplication.java      ← executa a aplicação com o Testcontainers
        │   ├── arquitetura            ← ModularidadeTest (fronteiras entre módulos) e CamadasTest (ArchUnit: camadas dentro do módulo)
        │   ├── isolamento             ← IsolamentoMultiTenantIT: @TenantId e RLS (HU03)
        │   └── <um pacote por módulo>
        └── resources
            └── db/migration-teste     ← tabela registro_isolamento, usada só nos testes de isolamento
```

### Módulo com DDD tático completo

`escritorio`, `autorizacao`, `processo`, `atividade`, `financeiro`:

```
processo
├── (pacote raiz)                      ← API pública: ProcessoApi e eventos publicados
├── dominio
│   ├── modelo                         ← agregados, entidades, value objects
│   ├── servico                        ← serviços de domínio
│   └── repositorio                    ← portas de saída
├── aplicacao
│   ├── casodeuso                      ← portas de entrada (*UseCase) e implementações
│   ├── comando                        ← *Command
│   └── consulta                       ← *Query
├── adaptador
│   ├── entrada/web                    ← controllers REST, *Request/*Response
│   └── saida/persistencia             ← implementações JPA das portas
└── configuracao
```

### Módulo simplificado

`identidade`, `cliente`, `documento`, `notificacao`, `auditoria`:

```
cliente
├── (pacote raiz)                      ← ClienteApi e eventos publicados
├── dominio
├── aplicacao
├── adaptador
│   ├── entrada/web
│   └── saida/persistencia
└── configuracao                       ← quando necessário
```

### Regras de dependência

- Fluxo: adaptador de entrada → porta de entrada → aplicação → domínio → porta de saída → adaptador de saída.
- Um módulo acessa outro **somente pela API pública** (pacote raiz); nunca importa `dominio`, `aplicacao` ou `adaptador` de outro módulo.
- Cada módulo é dono das suas tabelas: sem JOIN nem relacionamento JPA entre módulos (referência apenas por ID).
- Efeitos colaterais (notificação, auditoria) por **eventos de domínio**.
- O `tenant_id` vem **somente do JWT**; toda tabela de negócio tem `tenant_id` e política de RLS criada no Flyway.

Cada pacote contém um `package-info.java` que descreve sua responsabilidade (e mantém as pastas versionadas no Git).
