-- Módulo identidade: gestão de usuários (HU08).
-- Ao desativar um usuário, os tokens emitidos até aquele momento deixam de valer, mesmo que
-- ele seja reativado antes de os tokens expirarem: o TenantDoTokenFiltro só aceita token com
-- iat >= sessoes_validas_desde. NULL: nenhuma sessão foi encerrada.
-- Gravado truncado em segundos, a mesma precisão do iat do JWT.

ALTER TABLE usuario ADD COLUMN sessoes_validas_desde TIMESTAMPTZ;
