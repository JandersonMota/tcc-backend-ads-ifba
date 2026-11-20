-- Módulo identidade: usuários do escritório (HU05).
-- grupo_id referencia o módulo autorizacao: só o ID, sem FK (cada módulo é dono das suas tabelas).
-- O e-mail é o login e é único na PLATAFORMA (não por tenant). A restrição única vale para a
-- tabela inteira mesmo com RLS, então detecta e-mail repetido de outro escritório sem consulta
-- prévia e sem condição de corrida.

CREATE TABLE usuario (
    id           UUID         NOT NULL PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES escritorio (id),
    grupo_id     UUID         NOT NULL,
    nome         VARCHAR(200) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    senha_hash   VARCHAR(255) NOT NULL,
    proprietario BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,

    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_email_minusculo CHECK (email = lower(email))
);

CREATE INDEX idx_usuario_tenant ON usuario (tenant_id);

-- RN03: um único Proprietário por escritório.
CREATE UNIQUE INDEX ux_usuario_proprietario ON usuario (tenant_id) WHERE proprietario;

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON usuario
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
