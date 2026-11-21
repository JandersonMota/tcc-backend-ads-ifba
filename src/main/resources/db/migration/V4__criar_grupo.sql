-- Módulo autorizacao: grupos e permissões padrão de cada grupo (HU05, RF05).
-- permissao_individual (RF07, desejável) fica para uma migração futura.

CREATE TABLE grupo (
    id         UUID        NOT NULL PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES escritorio (id),
    nome       VARCHAR(50) NOT NULL,
    padrao     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,

    CONSTRAINT uk_grupo_tenant_nome UNIQUE (tenant_id, nome)
);

CREATE INDEX idx_grupo_tenant ON grupo (tenant_id);

ALTER TABLE grupo ENABLE ROW LEVEL SECURITY;
ALTER TABLE grupo FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON grupo
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Tabela de coleção (@ElementCollection): o Hibernate não preenche @TenantId aqui, então
-- tenant_id vem do tenant da transação (DEFAULT). O RLS (WITH CHECK) continua validando o valor.
CREATE TABLE grupo_permissao (
    tenant_id UUID        NOT NULL DEFAULT NULLIF(current_setting('app.tenant_id', true), '')::uuid
                          REFERENCES escritorio (id),
    grupo_id  UUID        NOT NULL REFERENCES grupo (id) ON DELETE CASCADE,
    permissao VARCHAR(60) NOT NULL,

    CONSTRAINT pk_grupo_permissao PRIMARY KEY (grupo_id, permissao)
);

CREATE INDEX idx_grupo_permissao_tenant ON grupo_permissao (tenant_id);

ALTER TABLE grupo_permissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE grupo_permissao FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON grupo_permissao
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
