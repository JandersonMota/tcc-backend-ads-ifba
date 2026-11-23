# Multi-tenancy, autenticação e autorização

## Estratégia

**Schema compartilhado + coluna `tenant_id`**, com duas camadas de defesa:

```
Requisição → JWT → tenant_id → TenantContext → Filtro da aplicação (Hibernate @TenantId) → RLS no PostgreSQL
```

Mesmo que alguém esqueça um `WHERE tenant_id = ?`, o banco impede o acesso a dados de outro escritório.

Alternativas descartadas (não proponha sem pedido explícito): schema por tenant (complica Flyway, testes e operação) e banco por tenant (excessivo para o MVP).

A infraestrutura já está implementada (HU03, ADR-012) em `compartilhado`. **Reutilize; não recrie.**

| Classe | Pacote | Papel |
|---|---|---|
| `TenantContext` | `compartilhado.infraestrutura.tenant` | Tenant e usuário da execução atual (`ScopedValue`) |
| `TenantIdentifierResolver` | idem | Informa ao Hibernate o tenant da sessão (camada 1) |
| `TenantJpaTransactionManager` | idem | `set_config('app.tenant_id', …, true)` no início de cada transação (camada 2) |
| `ColunasDeControleInterceptor` | idem | Preenche `created_at`, `updated_at`, `created_by` |
| `EntidadeTenant` | `compartilhado.dominio` | Base das entidades de negócio (`id`, `@TenantId tenantId`, colunas de controle) |
| `EventoDominio` | `compartilhado.dominio` | Interface dos eventos (`tenantId()`, `ocorridoEm()`) |

## Origem do tenant

- O `tenant_id` é uma **claim do JWT** emitido no login. Nunca vem de parâmetro, path, header ou corpo.
- `TenantContext` guarda o tenant (e o usuário) da execução em um `ScopedValue`: vale só dentro de `executarComo`, não vaza entre requisições nem entre threads.
- Quem chama `executarComo` é sempre um **adaptador de entrada confiável**:
  - HTTP: `TenantDoTokenFiltro` (`compartilhado.infraestrutura.seguranca`), a partir das claims `tenant_id` e `sub` do JWT validado (HU06); já no tenant, recusa token cujo `jti` foi revogado no logout (HU07);
  - listener de evento: a partir de `evento.tenantId()` (ver `comunicacao-eventos.md`);
  - testes: diretamente.
- Casos de uso e domínio **leem** (`tenantAtual()`, `usuarioAtual()`), nunca definem.
- Operações sem tenant (criar escritório, login) são exceções explícitas e ficam restritas aos módulos `escritorio` e `identidade`. No login, a única leitura sem tenant é a função `credencial_por_email` (`V5`, ADR-015); na limpeza do logout, a função `remover_tokens_revogados_vencidos` (`V6`, ADR-016), que só apaga revogações vencidas. Nunca crie outra política ou função que ignore o RLS sem uma ADR.

```java
// leitura (caso de uso, adaptador)
UUID escritorio = tenantContext.tenantAtual();        // TenantNaoDefinidoException se não houver
Optional<UUID> usuario = tenantContext.usuarioOpcional();

// definição (somente adaptadores de entrada e testes)
tenantContext.executarComo(tenantId, usuarioId, () -> casoDeUso.executar(comando));
```

### Tenant antes da transação

O tenant deve ser definido **antes** de abrir a transação: é no início dela que o Hibernate fixa o tenant da sessão e que o `set_config` é executado. `executarComo` com transação já ativa lança `IllegalStateException` (falha cedo em vez de vazar). Consequências:

- Controllers e listeners **não** são `@Transactional`; quem abre a transação é o caso de uso.
- Não use `@ApplicationModuleListener` em listeners que acessam dados (ele abre a transação antes do método).

### Acesso a dados sempre dentro de transação

Fora de transação não há `app.tenant_id`, e o RLS não retorna nenhuma linha (falha segura). Métodos de consulta declarados em interfaces do Spring Data (`@Query`, `findByX`) **não são transacionais por padrão**: chame-os a partir de um caso de uso `@Transactional` (ou `@Transactional(readOnly = true)` nas consultas).

## Camada 1: Hibernate `@TenantId`

Entidades de negócio estendem `EntidadeTenant`, que já declara o `@TenantId`:

```java
@Entity
@Table(name = "cliente")
class ClienteEntity extends EntidadeTenant {
    @Column(nullable = false, length = 200)
    private String nome;
    // ...
}
```

- O Hibernate preenche `tenant_id` ao inserir e filtra as consultas JPQL/Spring Data e as buscas por ID com o tenant do `TenantIdentifierResolver`.
- `tenantId` não tem setter e nunca aparece em DTO de entrada.
- Sem tenant, o resolver devolve um UUID que não corresponde a nenhum escritório: consultas voltam vazias e inserções são rejeitadas.
- Consultas nativas (`nativeQuery = true`) e JDBC **não** recebem o filtro do Hibernate; nelas, o RLS é a proteção. Prefira JPQL/Spring Data.

## Camada 2: Row-Level Security

`TenantJpaTransactionManager` executa no início de **cada** transação:

```sql
SELECT set_config('app.tenant_id', '<tenant ou vazio>', true);  -- true = vale só para a transação
```

JDBC (`JdbcTemplate`) dentro da transação usa a mesma conexão e também fica sujeito ao RLS.

Modelo de migração Flyway para tabela de negócio:

```sql
-- V8__criar_cliente.sql
CREATE TABLE cliente (
    id         UUID         NOT NULL PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES escritorio (id),
    nome       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by UUID
);

CREATE INDEX idx_cliente_tenant ON cliente (tenant_id);

ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON cliente
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Cuidados:
- Sem tenant definido, a expressão resulta em `NULL` e **nenhuma linha** é retornada (falha segura).
- A aplicação conecta como `app_user` (**sem `SUPERUSER` nem `BYPASSRLS`**); o Flyway conecta como `app_owner`, dono das tabelas. Vale também nos testes (`TestcontainersConfiguration`).
- `FORCE ROW LEVEL SECURITY` faz a política valer também para o dono da tabela.
- Índices e chaves únicas de negócio começam por `tenant_id` (ex.: `UNIQUE (tenant_id, numero)`). Exceção: `usuario.email` é único na plataforma (`uk_usuario_email`); a restrição vale para a tabela inteira mesmo com RLS.
- **Tabelas de coleção** (`@ElementCollection`, ex.: `grupo_permissao`): o Hibernate não preenche `@TenantId` nelas. Use `tenant_id UUID NOT NULL DEFAULT NULLIF(current_setting('app.tenant_id', true), '')::uuid`; o `WITH CHECK` continua validando (ver `V4__criar_grupo.sql`).
- Toda tabela com `tenant_id` precisa de RLS `ENABLE` + `FORCE` e de política: o `RlsTodaTabelaNegocioIT` falha caso contrário.
- O modelo de referência, testado, é `src/test/resources/db/migration-teste/V9999__criar_registro_isolamento.sql`.

## Migrações Flyway

- Padrão de nome: `V{n}__criar_{tabela}.sql`. Já existem `V1__criar_event_publication.sql` (Spring Modulith), `V2__criar_escritorio.sql`, `V3__criar_usuario.sql` e `V4__criar_grupo.sql` (HU05) , `V5__criar_funcao_credencial_login.sql` (HU06) , `V6__criar_token_revogado.sql` (HU07) e `V7__adicionar_sessoes_validas_desde_usuario.sql` (HU08); a próxima é `V8`. Alteração de tabela existente usa `V{n}__adicionar_{coluna}_{tabela}.sql`.
- Uma migração aplicada nunca é editada; correções vão em nova versão.
- Toda tabela de negócio nasce com `tenant_id`, índice e RLS na **mesma** migração.
- Tabelas pertencem a um único módulo; não crie FKs entre tabelas de módulos diferentes (exceto para `escritorio`, que é referência comum).
- `escritorio` não tem RLS por `tenant_id` (é o próprio tenant).
- Migrações só de teste ficam em `src/test/resources/db/migration-teste`, com versão alta (`V9999`).

## Autorização

O MVP exige controle granular de permissões com grupos (módulo `autorizacao`).

Convenção (catálogo e grupos padrão em `doc-mvp/10-seguranca-e-conformidade/matriz-de-permissoes.md`, no repositório `tcc-ads-ifba`):
- Permissões no formato `RECURSO_ACAO` em português, conforme o projeto completo (ex.: `PROCESSO_CRIAR`, `CLIENTE_VISUALIZAR`, `FINANCEIRO_PAGAMENTO_REGISTRAR`).
- Usuário pertence a grupos; grupos agregam permissões; as permissões efetivas viram authorities no token ou são carregadas na autenticação.
- Endpoints protegidos com `@PreAuthorize("hasAuthority('...')")` (habilitar `@EnableMethodSecurity`).

Respostas:
- Sem permissão → **403**.
- Recurso de outro escritório → **404** (com o filtro de tenant, o recurso simplesmente não é encontrado; não revele que existe).

Exceções de `compartilhado.excecao`, traduzidas pelo `TratadorErros` (`type` `/problemas/<tipo>`): `ValidacaoException` 400, `AutenticacaoException` 401, `AcessoNegadoException` 403 (regra que depende do recurso, ex.: só o proprietário altera o próprio cadastro), `RecursoNaoEncontradoException` 404 (crie uma subclasse por recurso, ex.: `UsuarioNaoEncontradoException`), `ConflitoException` 409, `RegraNegocioException` 422 (ex.: limite do plano) e `LimiteExcedidoException` 429.

Limites do plano (RN09): leia com `EscritorioApi.limitesDoPlano()` e conte os ativos sob uma trava consultiva por tenant (`pg_advisory_xact_lock`), como em `JpaUsuarioRepositorio.contarAtivosComTrava()`, para que duas criações simultâneas não passem juntas do limite.

Sessão do token: além de validar o JWT, o `TenantDoTokenFiltro` consulta a porta `SessoesAtivas`, que recusa token revogado no logout, de usuário desativado ou com `iat` anterior a `usuario.sessoes_validas_desde`.
