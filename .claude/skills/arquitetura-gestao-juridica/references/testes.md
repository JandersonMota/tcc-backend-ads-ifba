# Testes

| Tipo | O que cobre | Ferramenta |
|---|---|---|
| Unitário | Regras de negócio do domínio e casos de uso | JUnit (sem Spring, sem banco) |
| Integração | Persistência, APIs REST, migrações | Spring Boot Test + **Testcontainers** (PostgreSQL real) |
| Autorização | Permissões por endpoint | Spring Security Test |
| Isolamento multi-tenant | Filtro da aplicação **e** RLS | Testcontainers |
| Arquitetura | Fronteiras entre módulos e camadas | Spring Modulith (`ModularidadeTest`) / ArchUnit (`CamadasTest`) |

Não use H2 ou banco em memória: o RLS e os tipos do PostgreSQL só são testados de verdade no PostgreSQL.

## Unitário

Teste o comportamento do agregado, não getters/setters.

```java
@Test
void naoPermiteDelegarAtividadeConcluida() {
    var atividade = umaAtividadeConcluida();
    assertThrows(AtividadeConcluidaException.class, () -> atividade.delegarPara(outroUsuario()));
}
```

## Integração com Testcontainers

Reutilize o `TestcontainersConfiguration` do projeto, em vez de declarar um contêiner por classe. Ele sobe o PostgreSQL 18, roda `docker/postgres/01-criar-usuarios.sh` e conecta o **Flyway como `app_owner`** e a **aplicação como `app_user`** (sem `SUPERUSER`/`BYPASSRLS`, igual à produção), de modo que o RLS vale nos testes. Por isso não usa `@ServiceConnection`; as propriedades vêm de um `DynamicPropertyRegistrar`.

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClienteIsolamentoIT {

    // as migrações Flyway rodam na inicialização, criando tabelas e políticas de RLS
}
```

- **Sufixos:** `*Test` roda no Surefire (`test`); `*IT` roda no **Failsafe** (`verify`). Ambos rodam no `./mvnw verify` e no CI. Um teste de integração com outro sufixo não roda.
- No Testcontainers 2 a classe é `org.testcontainers.postgresql.PostgreSQLContainer` (sem parâmetro genérico).
- Defina o tenant com `TenantContext.executarComo` e acesse dados **dentro de transação** (caso de uso `@Transactional` ou `TransactionTemplate`), como na aplicação real.
- Migrações só de teste ficam em `src/test/resources/db/migration-teste` (aplicadas apenas pelo `TestcontainersConfiguration`).

## Autorização

Para cada endpoint novo, cubra os quatro cenários:

```
Usuário A → recurso do próprio escritório        → PERMITIDO
Usuário A → recurso de outro escritório          → NEGADO (404)
Usuário sem permissão → ação protegida           → NEGADO (403)
Usuário com permissão → ação protegida           → PERMITIDO
```

## Isolamento multi-tenant

É o teste mais importante do projeto: comprova o diferencial do MVP. Deve validar **as duas camadas**:

1. **Aplicação:** usuário do Escritório A tenta ler, alterar e excluir recurso do Escritório B pela API → não encontra.
2. **Banco (RLS):** com `app.tenant_id` do Escritório A, uma consulta SQL nativa **sem** `WHERE tenant_id` não retorna linhas do Escritório B; e um `INSERT` com `tenant_id` do Escritório B é rejeitado pelo `WITH CHECK`.
3. **Sem tenant definido:** consultas não retornam nenhuma linha.

Inclua também tentativas de manipulação da requisição (ex.: enviar `tenantId` no corpo ou em header) e verifique que são ignoradas.

A infraestrutura (HU03) é coberta por `src/test/java/br/com/juridora/isolamento/IsolamentoMultiTenantIT.java`, sobre a tabela de teste `registro_isolamento`. Use-o como modelo para os testes de isolamento de cada recurso novo. Ele inclui:
- `CamadaAplicacaoSozinha`: desliga o RLS da tabela (como `app_owner`) para provar que o `@TenantId` isola sozinho;
- `CamadaBanco`: SQL nativo/JDBC (sem filtro do Hibernate) para provar que o RLS isola sozinho;
- pool com **uma conexão** para provar que o tenant não vaza para a transação seguinte.

## Arquitetura

```java
package br.com.juridora.arquitetura;

class ModularidadeTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(JuridoraApiApplication.class);

    @Test
    void deveRespeitarFronteirasEntreModulos() {
        MODULOS.verify();
    }
}
```

Esse teste já existe em `src/test/java/br/com/juridora/arquitetura/ModularidadeTest.java`.

`verify()` falha se um módulo acessar tipos internos de outro ou se houver ciclos entre módulos.

As camadas **dentro** de cada módulo são verificadas pelo ArchUnit em `src/test/java/br/com/juridora/arquitetura/CamadasTest.java` (ADR-008):

| Regra | O que proíbe |
|---|---|
| `dominioNaoDependeDeAplicacaoAdaptadorNemInfraestrutura` | `dominio` usando `aplicacao`, `adaptador`, `configuracao` ou `compartilhado.infraestrutura` |
| `dominioNaoDependeDeWebSegurancaNemClientesHttp` | `dominio` usando Spring Web/HTTP, Spring Security, Servlet, `java.net.http`, SpringDoc/Swagger |
| `aplicacaoNaoDependeDeAdaptadores` | `aplicacao` usando qualquer `adaptador` |
| `controllersFicamApenasEmAdaptadorEntradaWeb` | `@Controller`/`@RestController` fora de `adaptador.entrada.web` |
| `controllersTemSufixoController` | Controller sem o sufixo `Controller` |
| `adaptadoresDeEntradaNaoAcessamRepositoriosNemAdaptadoresDeSaida` | `adaptador.entrada` usando `dominio.repositorio`, `adaptador.saida`, Spring Data ou JPA |
| `dtosDeEntradaNaoTemCampoDeTenant` | `*Request` com campo `tenantId` (ou similar) |
| `controllersNaoRecebemTenantPorParametroHeaderOuPath` | `@RequestParam`/`@RequestHeader`/`@PathVariable` com "tenant" no nome |
| `semInjecaoPorCampo` | `@Autowired`/`@Inject` em campos (usar construtor) |

As regras usam `allowEmptyShould(true)`: passam com os pacotes vazios e valem automaticamente quando o código chegar. Ao criar uma regra nova, confirme que ela falha com uma classe que a viola antes de confiar nela.

O ArchUnit chega transitivamente pelo Spring Modulith. **Não o declare no `pom.xml` com `<scope>test</scope>`**: o `spring-modulith-runtime` usa o ArchUnit em tempo de execução, e a declaração direta rebaixaria o escopo, fazendo a aplicação falhar ao subir (`NoClassDefFoundError: com/tngtech/archunit/core/importer/ImportOption`).

Spring Modulith também gera documentação dos módulos (`Documenter`), útil para o documento do TCC. O `ModularidadeTest` já a gera em `target/spring-modulith-docs/`.
