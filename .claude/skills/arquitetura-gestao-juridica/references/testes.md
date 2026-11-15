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

Reutilize o `TestcontainersConfiguration` do projeto (PostgreSQL 18 com `@ServiceConnection`), em vez de declarar um contêiner por classe:

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClientePersistenciaIT {

    // as migrações Flyway rodam na inicialização, criando tabelas e políticas de RLS
}
```

No Testcontainers 2 a classe é `org.testcontainers.postgresql.PostgreSQLContainer` (sem parâmetro genérico).

Para validar o RLS, a aplicação no teste deve conectar com o mesmo tipo de usuário da produção (sem `SUPERUSER`/`BYPASSRLS`).

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

Spring Modulith também gera documentação dos módulos (`Documenter`), útil para o documento do TCC. O `ModularidadeTest` já a gera em `target/spring-modulith-docs/`.
