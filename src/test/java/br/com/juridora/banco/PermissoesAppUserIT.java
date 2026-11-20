package br.com.juridora.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import br.com.juridora.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU02 – a aplicação conecta como {@code app_user}, que só faz DML: o schema é
 * criado e alterado apenas pelo Flyway, como {@code app_owner} (ADR-003).
 * <p>
 * Se o {@code app_user} pudesse criar ou alterar tabelas, poderia também desligar o
 * RLS ({@code ALTER TABLE ... DISABLE ROW LEVEL SECURITY}) e quebrar o isolamento.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PermissoesAppUserIT {

	/** {@code insufficient_privilege}: falta de permissão. */
	private static final String SEM_PERMISSAO = "42501";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aplicacaoConectaComoAppUser() {
		assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("app_user");
	}

	@Test
	void usuariosDoSistemaNaoSaoSuperusuariosNemIgnoramRls() {
		List<Map<String, Object>> papeis = jdbc.queryForList("""
				SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
				FROM pg_roles WHERE rolname IN ('app_owner', 'app_user')
				""");

		assertThat(papeis).hasSize(2).allSatisfy(papel -> assertThat(papel)
				.containsEntry("rolsuper", false)
				.containsEntry("rolbypassrls", false)
				.containsEntry("rolcreatedb", false)
				.containsEntry("rolcreaterole", false));
	}

	@Test
	void flywayAplicaAsMigracoesComoAppOwner() {
		assertThat(jdbc.queryForList(
				"SELECT DISTINCT installed_by FROM flyway_schema_history WHERE success", String.class))
				.containsExactly("app_owner");
		assertThat(jdbc.queryForList(
				"SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public'", String.class))
				.containsExactly("app_owner");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"CREATE TABLE tabela_indevida (id UUID PRIMARY KEY)",
			"CREATE TABLE public.tabela_indevida AS SELECT * FROM escritorio",
			"CREATE INDEX ix_indevido ON escritorio (nome)",
			"CREATE VIEW visao_indevida AS SELECT * FROM escritorio",
			"CREATE SCHEMA schema_indevido"
	})
	void appUserNaoCriaObjetos(String ddl) {
		assertThat(sqlStateAoExecutar(ddl)).isEqualTo(SEM_PERMISSAO);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"ALTER TABLE escritorio ADD COLUMN coluna_indevida TEXT",
			"ALTER TABLE escritorio DROP COLUMN nome",
			"ALTER TABLE escritorio RENAME TO escritorio_renomeado",
			"ALTER TABLE registro_isolamento DISABLE ROW LEVEL SECURITY",
			"ALTER TABLE registro_isolamento NO FORCE ROW LEVEL SECURITY",
			"DROP POLICY isolamento_tenant ON registro_isolamento",
			"DROP TABLE registro_isolamento",
			"TRUNCATE escritorio"
	})
	void appUserNaoAlteraNemRemoveTabelas(String ddl) {
		assertThat(sqlStateAoExecutar(ddl)).isEqualTo(SEM_PERMISSAO);
	}

	/** Executa o comando, que deve falhar, e devolve o SQLSTATE do erro do PostgreSQL. */
	private String sqlStateAoExecutar(String sql) {
		Throwable erro = catchThrowable(() -> jdbc.execute(sql));

		assertThat(erro).as("o app_user não deveria conseguir executar: %s", sql)
				.isInstanceOf(DataAccessException.class);
		for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
			if (causa instanceof SQLException excecaoSql) {
				return excecaoSql.getSQLState();
			}
		}
		throw new AssertionError("erro sem SQLException na causa: " + erro);
	}

}
