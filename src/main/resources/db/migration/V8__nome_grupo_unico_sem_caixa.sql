-- Módulo autorizacao (HU09): grupos personalizados.
-- O nome do grupo passa a ser único no escritório sem diferenciar maiúsculas de minúsculas
-- ("Financeiro" e "financeiro" seriam dois grupos indistinguíveis na tela).

ALTER TABLE grupo DROP CONSTRAINT uk_grupo_tenant_nome;

CREATE UNIQUE INDEX ux_grupo_tenant_nome ON grupo (tenant_id, lower(nome));
