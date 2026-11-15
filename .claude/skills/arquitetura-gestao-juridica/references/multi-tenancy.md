# Multi-tenancy, autenticação e autorização

## Estratégia

**Schema compartilhado + coluna `tenant_id`**, com duas camadas de defesa:

```
Requisição → JWT → tenant_id → Filtro da aplicação (Hibernate @TenantId) → RLS no PostgreSQL
```

Mesmo que alguém esqueça um `WHERE tenant_id = ?`, o banco impede o acesso a dados de outro escritório.

Alternativas descartadas (não proponha sem pedido explícito): schema por tenant (complica Flyway, testes e operação) e banco por tenant (excessivo para o MVP).

## Origem do tenant

- O `tenant_id` é uma **claim do JWT** emitido no login.
- É lido do `SecurityContext` em um componente do `compartilhado` (ex.: `TenantContext`), nunca de parâmetro, path, header ou corpo.
- Operações sem tenant (criar escritório, login) são exceções explícitas e ficam restritas aos módulos `escritorio` e `identidade`.

```java
package br.com.juridora.compartilhado.infraestrutura.tenant;

@Component
public class TenantContext {

    public UUID currentTenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
        }
        throw new IllegalStateException("Requisição sem tenant autenticado");
    }
}
```

## Camada 1: Hibernate `@TenantId`

```java
@Entity
@Table(name = "cliente")
class ClienteEntity {
    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    // ...
}
```

O Hibernate preenche `tenant_id` ao inserir e filtra as consultas automaticamente, usando um `CurrentTenantIdentifierResolver`:

```java
@Component
class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    private final TenantContext tenantContext;

    TenantIdentifierResolver(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return tenantContext.currentTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> properties) {
        properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
```

Atenção: consultas nativas (`nativeQuery = true`) **não** recebem o filtro do Hibernate; nelas, o RLS é a proteção. Prefira JPQL/Spring Data.

## Camada 2: Row-Level Security

A aplicação define o tenant da transação no PostgreSQL e a política filtra as linhas:

```sql
-- executado no início de cada transação de negócio (ex.: aspecto nos casos de uso @Transactional)
SELECT set_config('app.tenant_id', :tenantId, true);  -- true = vale só para a transação
```

Modelo de migração Flyway para tabela de negócio:

```sql
-- V3__criar_cliente.sql
CREATE TABLE cliente (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES escritorio (id),
    nome       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cliente_tenant ON cliente (tenant_id);

ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON cliente
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

Cuidados:
- Sem tenant definido, a expressão resulta em `NULL` e **nenhuma linha** é retornada (falha segura).
- A aplicação deve conectar com um usuário **sem `SUPERUSER` nem `BYPASSRLS`**; o Flyway pode usar um usuário separado, dono das tabelas.
- `FORCE ROW LEVEL SECURITY` faz a política valer também para o dono da tabela.
- Índices e chaves únicas de negócio começam por `tenant_id` (ex.: `UNIQUE (tenant_id, numero)`).

## Migrações Flyway

- Padrão de nome do README: `V{n}__criar_{tabela}.sql` (ex.: `V1__criar_escritorio.sql`, `V4__criar_processo.sql`).
- Uma migração aplicada nunca é editada; correções vão em nova versão.
- Toda tabela de negócio nasce com `tenant_id`, índice e RLS na **mesma** migração.
- Tabelas pertencem a um único módulo; não crie FKs entre tabelas de módulos diferentes (exceto para `escritorio`, que é referência comum).

## Autorização

O MVP exige controle granular de permissões com grupos (módulo `autorizacao`).

Convenção (catálogo e grupos padrão em `doc-mvp/10-seguranca-e-conformidade/matriz-de-permissoes.md`, no repositório `tcc-ads-ifba`):
- Permissões no formato `RECURSO_ACAO` em português, conforme o projeto completo (ex.: `PROCESSO_CRIAR`, `CLIENTE_VISUALIZAR`, `FINANCEIRO_PAGAMENTO_REGISTRAR`).
- Usuário pertence a grupos; grupos agregam permissões; as permissões efetivas viram authorities no token ou são carregadas na autenticação.
- Endpoints protegidos com `@PreAuthorize("hasAuthority('...')")` (habilitar `@EnableMethodSecurity`).

Respostas:
- Sem permissão → **403**.
- Recurso de outro escritório → **404** (com o filtro de tenant, o recurso simplesmente não é encontrado; não revele que existe).
