package br.com.juridora.isolamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import br.com.juridora.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regra de arquitetura no banco (RNF01, ADR-003): toda tabela com {@code tenant_id}
 * precisa de RLS ligado e forçado ({@code ENABLE} + {@code FORCE}) e de ao menos uma
 * política. Pega migrações novas que esquecerem o RLS.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RlsTodaTabelaNegocioIT {

	private static final String TABELAS_COM_TENANT = """
			SELECT c.relname
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
			WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void tabelasDeNegocioConhecidasTemTenantId() {
		assertThat(jdbc.queryForList(TABELAS_COM_TENANT, String.class))
				.contains("usuario", "grupo", "grupo_permissao", "token_revogado", "registro_isolamento");
	}

	@Test
	void todaTabelaComTenantIdTemRlsLigadoEForcado() {
		List<String> semRls = jdbc.queryForList(TABELAS_COM_TENANT
				+ " AND NOT (c.relrowsecurity AND c.relforcerowsecurity)", String.class);

		assertThat(semRls).as("tabelas com tenant_id sem RLS ENABLE + FORCE").isEmpty();
	}

	@Test
	void todaTabelaComTenantIdTemPolitica() {
		List<String> semPolitica = jdbc.queryForList(TABELAS_COM_TENANT
				+ " AND NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid)", String.class);

		assertThat(semPolitica).as("tabelas com tenant_id sem política de RLS").isEmpty();
	}

}
