#!/bin/bash
# Cria os usuários do Juridora (executado só na primeira inicialização do volume).
#   app_owner: dono do schema e das tabelas; usado pelo Flyway (migrações e políticas de RLS).
#   app_user : usado pela aplicação; sem SUPERUSER/BYPASSRLS, só DML. Assim o RLS vale de verdade.
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_owner_senha="$APP_OWNER_SENHA" -v app_user_senha="$APP_USER_SENHA" <<-'EOSQL'
	CREATE ROLE app_owner LOGIN PASSWORD :'app_owner_senha'
	    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
	CREATE ROLE app_user LOGIN PASSWORD :'app_user_senha'
	    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

	-- Ninguém além dos usuários do sistema acessa o banco.
	REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
	GRANT CONNECT, TEMPORARY ON DATABASE :"DBNAME" TO app_owner, app_user;

	ALTER SCHEMA public OWNER TO app_owner;
	REVOKE ALL ON SCHEMA public FROM PUBLIC;
	GRANT USAGE ON SCHEMA public TO app_user;

	-- Tudo o que o app_owner (Flyway) criar fica acessível ao app_user apenas para DML.
	ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public
	    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
	ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public
	    GRANT USAGE, SELECT ON SEQUENCES TO app_user;
	ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public
	    GRANT EXECUTE ON FUNCTIONS TO app_user;
EOSQL
