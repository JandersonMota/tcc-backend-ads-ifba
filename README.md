# tcc-backend-ads-ifba

Backend do **Juridora**, sistema web multi-tenant de gestão jurídica para escritórios de advocacia (MVP do TCC – ADS/IFBA).

Arquitetura: **monólito modular** (Spring Modulith) + **DDD** + **Hexagonal** (Ports and Adapters). As decisões estão em [`tcc-ads-ifba/README.md`](../tcc-ads-ifba/README.md) e em [`doc-mvp/04-arquitetura-e-design-tecnico`](../tcc-ads-ifba/doc-mvp/04-arquitetura-e-design-tecnico/).

## Stack

| Item | Tecnologia |
|---|---|
| Linguagem / build | Java 25 (LTS) · Maven 3.10.0 (Maven Wrapper incluído) |
| Framework | Spring Boot 4.1.2 (Spring Framework 7.0) |
| Persistência | Spring Data JPA / Hibernate 7.4 · PostgreSQL · Flyway 12.4 |
| Modularidade | Spring Modulith 2.1.1 (core, JPA, actuator, observability) |
| Segurança | Spring Security 7.1 + JWT (OAuth2 Resource Server) · Validation |
| API | Spring Web MVC · SpringDoc OpenAPI 3.2.0 (Swagger UI) |
| Observabilidade | Spring Boot Actuator |
| Testes | JUnit 6 · Testcontainers 2 (PostgreSQL) · Spring Modulith Test · ArchUnit *(a adicionar)* |

Versões não declaradas no `pom.xml` são as gerenciadas pelo Spring Boot 4.1.2. H2 não é usado: o RLS só é testado no PostgreSQL real.

## Como executar

Pré-requisitos: JDK 25 e Docker em execução.

```bash
./mvnw spring-boot:test-run    # sobe a aplicação com PostgreSQL via Testcontainers
./mvnw verify                  # compila e executa todos os testes
```

No Windows, use `mvnw.cmd`. No IntelliJ, execute `TestTccBackendAdsIfbaApplication` (em `src/test/java`) para subir com o banco do Testcontainers, ou `TccBackendAdsIfbaApplication` com um PostgreSQL configurado.

| Endereço | Conteúdo |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | Especificação OpenAPI |
| `http://localhost:8080/actuator/health` | Estado da aplicação |

Configuração completa do ambiente: [`configuracao-do-ambiente.md`](../tcc-ads-ifba/doc-mvp/07-codigo-e-desenvolvimento/configuracao-do-ambiente.md).

## Estrutura

```
tcc-backend-ads-ifba
├── docker/postgres                    ← scripts do PostgreSQL local (usuários app_owner/app_user)
└── src
    ├── main
    │   ├── java/br/com/leija
    │   │   ├── TccBackendAdsIfbaApplication.java  ← classe principal (pacote raiz)
    │   │   ├── compartilhado          ← tipos base, exceções, TenantContext, segurança
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
    │       └── db/migration           ← migrações Flyway (V{n}__descricao.sql), com RLS
    └── test
        ├── java/br/com/leija
        │   ├── TestcontainersConfiguration.java    ← PostgreSQL em contêiner (@ServiceConnection)
        │   ├── TestTccBackendAdsIfbaApplication.java ← executa a aplicação com o Testcontainers
        │   ├── TccBackendAdsIfbaApplicationTests.java
        │   ├── arquitetura            ← ApplicationModules.verify() e ArchUnit
        │   ├── isolamento             ← testes multi-tenant com Testcontainers
        │   └── <um pacote por módulo>
        └── resources
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
