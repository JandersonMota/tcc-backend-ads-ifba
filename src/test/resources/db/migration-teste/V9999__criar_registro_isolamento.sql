-- Tabela usada SOMENTE nos testes de isolamento multi-tenant (HU03).
-- Segue exatamente o modelo de toda tabela de negócio: tenant_id com FK para
-- escritorio, índice iniciando por tenant_id, colunas de controle e RLS
-- (ENABLE + FORCE) com USING e WITH CHECK.
-- Versão alta para ser aplicada sempre depois das migrações reais.

CREATE TABLE registro_isolamento (
    id         UUID         NOT NULL PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES escritorio (id),
    descricao  VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by UUID
);

CREATE INDEX idx_registro_isolamento_tenant ON registro_isolamento (tenant_id);

ALTER TABLE registro_isolamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE registro_isolamento FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON registro_isolamento
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
