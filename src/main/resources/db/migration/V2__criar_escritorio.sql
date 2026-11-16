-- Módulo escritorio: o escritório é o próprio tenant.
-- Toda tabela de negócio referencia escritorio(id) em tenant_id (única FK permitida
-- entre módulos, ver Modelo de Dados). Esta tabela não tem RLS por tenant_id:
-- o acesso fica restrito aos módulos escritorio e identidade.

CREATE TABLE escritorio (
    id         UUID         NOT NULL PRIMARY KEY,
    nome       VARCHAR(200) NOT NULL,
    plano      VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_escritorio_plano CHECK (plano IN ('FREE'))
);
