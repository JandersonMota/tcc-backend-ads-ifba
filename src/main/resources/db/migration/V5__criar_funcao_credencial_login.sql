-- Módulo identidade: busca da credencial no login (HU06, ADR-015).
-- No login ainda não se sabe o tenant, e o RLS de usuario não devolve nenhuma linha sem ele.
-- A única exceção ao RLS é esta função: devolve só id, tenant_id, senha_hash e ativo de UM e-mail.
--
-- Como usuario tem FORCE ROW LEVEL SECURITY, nem o dono (app_owner) lê sem política. A política
-- abaixo vale só para o papel app_owner, e o app_user (que não é membro do app_owner) só chega
-- nesse papel executando a função SECURITY DEFINER.

CREATE POLICY leitura_credencial_login ON usuario
    FOR SELECT
    TO app_owner
    USING (true);

CREATE FUNCTION credencial_por_email(p_email VARCHAR)
    RETURNS TABLE (id UUID, tenant_id UUID, senha_hash VARCHAR, ativo BOOLEAN)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    -- search_path fixo: impede que objetos de outro schema sejam usados no lugar dos nossos.
    SET search_path = public, pg_temp
AS $$
    SELECT u.id, u.tenant_id, u.senha_hash, u.ativo
      FROM usuario u
     WHERE u.email = lower(p_email)
$$;

REVOKE ALL ON FUNCTION credencial_por_email(VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION credencial_por_email(VARCHAR) TO app_user;
