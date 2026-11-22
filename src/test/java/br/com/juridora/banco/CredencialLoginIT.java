package br.com.juridora.banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU06, ADR-015 – a função {@code credencial_por_email} é a única leitura de
 * {@code usuario} sem tenant, e ela não abre o RLS para o {@code app_user}.
 * As consultas rodam como {@code app_user}, a conexão da aplicação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CredencialLoginIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	TenantContext tenantContext;

	@Test
	void funcaoAchaACredencialSemTenant() {
		Cadastro cadastro = new ApoioApi(mvc).cadastrarEscritorio();

		Map<String, Object> credencial = jdbc.queryForMap("SELECT * FROM credencial_por_email(?)",
				cadastro.email().toUpperCase());

		assertThat(credencial).containsOnlyKeys("id", "tenant_id", "senha_hash", "ativo")
				.containsEntry("id", cadastro.usuarioId())
				.containsEntry("tenant_id", cadastro.escritorioId())
				.containsEntry("ativo", true);
	}

	@Test
	void emailInexistenteNaoDevolveNada() {
		assertThat(jdbc.queryForList("SELECT * FROM credencial_por_email(?)", ApoioApi.emailUnico())).isEmpty();
	}

	@Test
	void appUserContinuaSemLerUsuarioSemTenantNemDeOutroEscritorio() {
		Cadastro escritorioA = new ApoioApi(mvc).cadastrarEscritorio();
		Cadastro escritorioB = new ApoioApi(mvc).cadastrarEscritorio();

		Long semTenant = transacao.execute(_ -> jdbc.queryForObject(
				"SELECT count(*) FROM usuario WHERE email = ?", Long.class, escritorioA.email()));
		Long deOutroEscritorio = tenantContext.executarComo(escritorioB.escritorioId(), null,
				() -> transacao.execute(_ -> jdbc.queryForObject(
						"SELECT count(*) FROM usuario WHERE email = ?", Long.class, escritorioA.email())));

		assertThat(semTenant).isZero();
		assertThat(deOutroEscritorio).isZero();
	}

	@Test
	void politicaDeLeituraValeSoParaOAppOwner() {
		List<String> papeis = jdbc.queryForList(
				"SELECT unnest(roles)::text FROM pg_policies WHERE tablename = 'usuario' "
						+ "AND policyname = 'leitura_credencial_login'", String.class);
		Boolean appUserEhMembroDoAppOwner = jdbc.queryForObject(
				"SELECT pg_has_role('app_user', 'app_owner', 'MEMBER')", Boolean.class);

		assertThat(papeis).containsExactly("app_owner");
		assertThat(appUserEhMembroDoAppOwner).isFalse();
	}

	@Test
	void funcaoEhSecurityDefinerComSearchPathFixo() {
		Map<String, Object> funcao = jdbc.queryForMap(
				"SELECT p.prosecdef, p.proconfig::text AS config, pg_get_userbyid(p.proowner) AS dono "
						+ "FROM pg_proc p WHERE p.proname = 'credencial_por_email'");

		assertThat(funcao).containsEntry("prosecdef", true).containsEntry("dono", "app_owner");
		assertThat((String) funcao.get("config")).contains("search_path=public, pg_temp");
	}

}
