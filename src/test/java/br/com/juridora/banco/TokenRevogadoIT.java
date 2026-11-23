package br.com.juridora.banco;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU07, ADR-016 – a tabela {@code token_revogado} fica isolada por escritório (RLS), e a
 * única leitura e remoção fora do tenant é a função {@code remover_tokens_revogados_vencidos},
 * que só apaga registros vencidos. As consultas rodam como {@code app_user}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenRevogadoIT {

	private static final String CONTAR_POR_JTI = "SELECT count(*) FROM token_revogado WHERE jti = ?";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	TenantContext tenantContext;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	PostgreSQLContainer postgres;

	ApoioApi api;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
	}

	@Test
	void logoutGravaOTenantEOUsuarioDoToken() throws SQLException {
		Cadastro cadastro = api.cadastrarEscritorio();
		UUID jti = sairERetornarJti(cadastro);

		Map<String, Object> registro = comoSuperusuario(
				"SELECT tenant_id, usuario_id, expira_em > now() AS vigente FROM token_revogado WHERE jti = ?", jti);

		assertThat(registro).containsEntry("tenant_id", cadastro.escritorioId())
				.containsEntry("usuario_id", cadastro.usuarioId())
				.containsEntry("vigente", true);
	}

	@Test
	void appUserSoEnxergaARevogacaoNoProprioEscritorio() {
		Cadastro escritorioA = api.cadastrarEscritorio();
		Cadastro escritorioB = api.cadastrarEscritorio();
		UUID jti = sairERetornarJti(escritorioA);

		Long semTenant = transacao.execute(_ -> jdbc.queryForObject(CONTAR_POR_JTI, Long.class, jti));

		assertThat(contarComo(escritorioA.escritorioId(), jti)).isEqualTo(1);
		assertThat(contarComo(escritorioB.escritorioId(), jti)).isZero();
		assertThat(semTenant).isZero();
	}

	@Test
	void funcaoRemoveSoAsRevogacoesVencidas() throws SQLException {
		Cadastro cadastro = api.cadastrarEscritorio();
		UUID vencido = UUID.randomUUID();
		UUID vigente = sairERetornarJti(cadastro);
		inserirComoSuperusuario(vencido, cadastro, "now() - interval '1 hour'");

		Integer removidos = jdbc.queryForObject("SELECT remover_tokens_revogados_vencidos()", Integer.class);

		assertThat(removidos).isPositive();
		assertThat(existeComoSuperusuario(vencido)).isFalse();
		assertThat(existeComoSuperusuario(vigente)).isTrue();
	}

	@Test
	void appUserNaoApagaRevogacoesSemTenant() throws SQLException {
		Cadastro cadastro = api.cadastrarEscritorio();
		UUID vencido = UUID.randomUUID();
		inserirComoSuperusuario(vencido, cadastro, "now() - interval '1 hour'");

		Integer apagados = transacao.execute(_ -> jdbc.update("DELETE FROM token_revogado WHERE jti = ?", vencido));

		assertThat(apagados).isZero();
		assertThat(existeComoSuperusuario(vencido)).isTrue();
	}

	@Test
	void funcaoEhSecurityDefinerComSearchPathFixo() {
		Map<String, Object> funcao = jdbc.queryForMap(
				"SELECT p.prosecdef, p.proconfig::text AS config, pg_get_userbyid(p.proowner) AS dono "
						+ "FROM pg_proc p WHERE p.proname = 'remover_tokens_revogados_vencidos'");

		assertThat(funcao).containsEntry("prosecdef", true).containsEntry("dono", "app_owner");
		assertThat((String) funcao.get("config")).contains("search_path=public, pg_temp");
	}

	// ----------------------------------------------------------------- apoio

	private UUID sairERetornarJti(Cadastro cadastro) {
		String token = api.token(cadastro.email());
		api.sair(token);
		return UUID.fromString(jwtDecoder.decode(token).getId());
	}

	private Long contarComo(UUID tenantId, UUID jti) {
		return tenantContext.executarComo(tenantId, null,
				() -> transacao.execute(_ -> jdbc.queryForObject(CONTAR_POR_JTI, Long.class, jti)));
	}

	private Connection superusuario() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private Map<String, Object> comoSuperusuario(String sql, UUID jti) throws SQLException {
		try (Connection conexao = superusuario(); PreparedStatement comando = conexao.prepareStatement(sql)) {
			comando.setObject(1, jti);
			try (ResultSet linha = comando.executeQuery()) {
				assertThat(linha.next()).isTrue();
				return Map.of("tenant_id", linha.getObject("tenant_id", UUID.class),
						"usuario_id", linha.getObject("usuario_id", UUID.class),
						"vigente", linha.getBoolean("vigente"));
			}
		}
	}

	private void inserirComoSuperusuario(UUID jti, Cadastro cadastro, String expiraEm) throws SQLException {
		String sql = "INSERT INTO token_revogado (jti, tenant_id, usuario_id, expira_em) VALUES (?, ?, ?, "
				+ expiraEm + ")";
		try (Connection conexao = superusuario(); PreparedStatement comando = conexao.prepareStatement(sql)) {
			comando.setObject(1, jti);
			comando.setObject(2, cadastro.escritorioId());
			comando.setObject(3, cadastro.usuarioId());
			assertThat(comando.executeUpdate()).isEqualTo(1);
		}
	}

	private boolean existeComoSuperusuario(UUID jti) throws SQLException {
		try (Connection conexao = superusuario();
				PreparedStatement comando = conexao.prepareStatement(CONTAR_POR_JTI)) {
			comando.setObject(1, jti);
			try (ResultSet linha = comando.executeQuery()) {
				linha.next();
				return linha.getLong(1) > 0;
			}
		}
	}

}
