-- Módulo identidade: tokens encerrados no logout (HU07, RF03, ADR-016).
-- O JWT é stateless e vale até o exp (15 min). Para o logout invalidá-lo de verdade, o jti
-- fica aqui até o token expirar; o TenantDoTokenFiltro recusa todo token cujo jti esteja na tabela.
--
-- tenant_id vem do tenant da transação (DEFAULT), como em grupo_permissao: a aplicação não o
-- informa, e o RLS (WITH CHECK) valida o valor.

CREATE TABLE token_revogado (
    jti        UUID        NOT NULL PRIMARY KEY,
    tenant_id  UUID        NOT NULL DEFAULT NULLIF(current_setting('app.tenant_id', true), '')::uuid
                           REFERENCES escritorio (id),
    usuario_id UUID        NOT NULL,
    expira_em  TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_token_revogado_tenant ON token_revogado (tenant_id);
CREATE INDEX idx_token_revogado_expira_em ON token_revogado (expira_em);

ALTER TABLE token_revogado ENABLE ROW LEVEL SECURITY;
ALTER TABLE token_revogado FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_tenant ON token_revogado
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Limpeza dos registros vencidos, de todos os escritórios, por uma tarefa agendada (sem tenant).
-- Mesmo padrão da credencial_por_email (V5, ADR-015): a política vale só para o app_owner, e o
-- app_user só chega nesse papel executando a função SECURITY DEFINER, que apaga apenas registros
-- vencidos. A margem cobre a tolerância de relógio do validador do JWT (60 s).
-- DELETE com WHERE também passa pelas políticas de SELECT, por isso são duas políticas.

CREATE POLICY leitura_tokens_vencidos ON token_revogado
    FOR SELECT
    TO app_owner
    USING (expira_em < now() - interval '5 minutes');

CREATE POLICY limpeza_tokens_vencidos ON token_revogado
    FOR DELETE
    TO app_owner
    USING (expira_em < now() - interval '5 minutes');

CREATE FUNCTION remover_tokens_revogados_vencidos()
    RETURNS INTEGER
    LANGUAGE sql
    VOLATILE
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    WITH removidos AS (
        DELETE FROM token_revogado
         WHERE expira_em < now() - interval '5 minutes'
        RETURNING 1
    )
    SELECT count(*)::integer FROM removidos
$$;

REVOKE ALL ON FUNCTION remover_tokens_revogados_vencidos() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION remover_tokens_revogados_vencidos() TO app_user;
