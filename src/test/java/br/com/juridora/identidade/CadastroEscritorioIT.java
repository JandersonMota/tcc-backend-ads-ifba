package br.com.juridora.identidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import br.com.juridora.TestcontainersConfiguration;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU05 – cadastro de escritório pela API, com PostgreSQL real e a aplicação conectada
 * como {@code app_user} (RLS valendo). Casos de teste CT13 e CT14.
 * <p>
 * Conferências em tabelas com RLS rodam com o tenant definido e dentro de transação;
 * totais da plataforma inteira usam o superusuário do contêiner (ignora o RLS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CadastroEscritorioIT {

	private static final String CADASTRO = "/api/autenticacao/cadastro";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	TenantContext tenantContext;

	@Autowired
	PostgreSQLContainer postgres;

	@Nested
	class CadastroValido {

		@Test
		void criaEscritorioFreeComProprietarioEGruposPadrao() {
			String email = emailUnico();

			MvcTestResult resposta = cadastrar("Silva Advogados", "Ana Silva", email, "senha-forte-123");

			assertThat(resposta).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/escritorio");
			UUID escritorioId = UUID.fromString(campo(resposta, "escritorioId"));
			UUID usuarioId = UUID.fromString(campo(resposta, "usuarioId"));

			Map<String, Object> escritorio = jdbc.queryForMap("SELECT nome, plano, ativo FROM escritorio WHERE id = ?",
					escritorioId);
			assertThat(escritorio).containsEntry("nome", "Silva Advogados")
					.containsEntry("plano", "FREE")
					.containsEntry("ativo", true);

			Map<String, Object> usuario = noTenant(escritorioId, () -> jdbc.queryForMap(
					"SELECT u.tenant_id, u.nome, u.email, u.senha_hash, u.proprietario, u.ativo, g.nome AS grupo "
							+ "FROM usuario u JOIN grupo g ON g.id = u.grupo_id WHERE u.id = ?",
					usuarioId));
			assertThat(usuario).containsEntry("tenant_id", escritorioId)
					.containsEntry("nome", "Ana Silva")
					.containsEntry("email", email)
					.containsEntry("proprietario", true)
					.containsEntry("ativo", true)
					.containsEntry("grupo", "Proprietário");
			assertThat((String) usuario.get("senha_hash")).startsWith("{bcrypt}$2").doesNotContain("senha-forte-123");
		}

		@Test
		void criaOsCincoGruposPadraoComAsPermissoesDaMatriz() {
			UUID escritorioId = cadastrarValido(emailUnico());

			List<Map<String, Object>> grupos = noTenant(escritorioId, () -> jdbc.queryForList(
					"SELECT g.nome, g.padrao, count(p.permissao) AS permissoes FROM grupo g "
							+ "LEFT JOIN grupo_permissao p ON p.grupo_id = g.id GROUP BY g.nome, g.padrao"));

			assertThat(grupos).extracting(g -> g.get("nome"))
					.containsExactlyInAnyOrder("Proprietário", "Gerente", "Advogado", "Estagiário", "Usuário");
			assertThat(grupos).allSatisfy(g -> assertThat(g.get("padrao")).isEqualTo(true));
			assertThat(grupos).extracting(g -> g.get("nome") + "=" + g.get("permissoes"))
					.containsExactlyInAnyOrder("Proprietário=22", "Gerente=19", "Advogado=12", "Estagiário=8",
							"Usuário=6");

			List<UUID> tenantsDasPermissoes = noTenant(escritorioId, () -> jdbc.queryForList(
					"SELECT DISTINCT tenant_id FROM grupo_permissao", UUID.class));
			assertThat(tenantsDasPermissoes).containsExactly(escritorioId);
		}

		@Test
		void ignoraTenantIdEnviadoNoCorpo() {
			UUID forjado = UUID.randomUUID();
			String corpo = """
					{"nomeEscritorio": "Forjado", "nome": "Ana", "email": "%s", "senha": "senha-forte-123",
					 "tenantId": "%s"}""".formatted(emailUnico(), forjado);

			MvcTestResult resposta = mvc.post().uri(CADASTRO).contentType(MediaType.APPLICATION_JSON).content(corpo)
					.exchange();

			assertThat(resposta).hasStatus(HttpStatus.CREATED);
			assertThat(campo(resposta, "escritorioId")).isNotEqualTo(forjado.toString());
			assertThat(jdbc.queryForObject("SELECT count(*) FROM escritorio WHERE id = ?", Long.class, forjado))
					.isZero();
		}

	}

	@Nested
	class EmailJaCadastrado {

		@Test
		void segundoCadastroComOMesmoEmailRetorna409ENaoGravaNada() throws SQLException {
			String email = emailUnico();
			cadastrarValido(email);
			Totais antes = totais();

			MvcTestResult resposta = cadastrar("Outro Escritório", "Bruno", email, "outra-senha-123");

			assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
			assertThat(resposta).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
			assertThat(campo(resposta, "type")).isEqualTo("/problemas/email-ja-cadastrado");
			assertThat(corpo(resposta)).doesNotContain(email);
			assertThat(totais()).isEqualTo(antes);
		}

		@Test
		void emailComMaiusculasDiferentesEhOMesmoLogin() {
			String email = emailUnico();
			cadastrarValido(email);

			MvcTestResult resposta = cadastrar("Outro Escritório", "Bruno", email.toUpperCase(), "outra-senha-123");

			assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
		}

	}

	@Nested
	class DadosInvalidos {

		@ParameterizedTest
		@ValueSource(strings = {
				"""
				{"nomeEscritorio": "", "nome": "Ana", "email": "ana@x.com", "senha": "senha-forte-123"}""",
				"""
				{"nomeEscritorio": "Esc", "nome": " ", "email": "ana@x.com", "senha": "senha-forte-123"}""",
				"""
				{"nomeEscritorio": "Esc", "nome": "Ana", "email": "nao-e-email", "senha": "senha-forte-123"}""",
				"""
				{"nomeEscritorio": "Esc", "nome": "Ana", "email": "ana@x.com", "senha": "curta"}""",
				"""
				{"nomeEscritorio": "Esc", "nome": "Ana", "email": "ana@x.com"}""",
				"{}" })
		void retorna400ComOsCamposInvalidos(String corpo) throws SQLException {
			Totais antes = totais();

			MvcTestResult resposta = mvc.post().uri(CADASTRO).contentType(MediaType.APPLICATION_JSON).content(corpo)
					.exchange();

			assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(resposta).bodyJson().extractingPath("$.campos").asArray().isNotEmpty();
			assertThat(totais()).isEqualTo(antes);
		}

		@Test
		void senhaComMaisDe72CaracteresEhRejeitada() {
			MvcTestResult resposta = cadastrar("Esc", "Ana", emailUnico(), "a".repeat(73));

			assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
		}

	}

	@Test
	void cadaEscritorioSoEnxergaOProprioUsuarioEOsPropriosGrupos() {
		UUID escritorioA = cadastrarValido(emailUnico());
		UUID escritorioB = cadastrarValido(emailUnico());

		for (UUID escritorio : List.of(escritorioA, escritorioB)) {
			List<UUID> tenantsDeUsuario = noTenant(escritorio, () -> jdbc.queryForList(
					"SELECT tenant_id FROM usuario", UUID.class));
			List<UUID> tenantsDeGrupo = noTenant(escritorio, () -> jdbc.queryForList(
					"SELECT tenant_id FROM grupo", UUID.class));
			List<UUID> tenantsDePermissao = noTenant(escritorio, () -> jdbc.queryForList(
					"SELECT DISTINCT tenant_id FROM grupo_permissao", UUID.class));

			assertThat(tenantsDeUsuario).containsExactly(escritorio);
			assertThat(tenantsDeGrupo).hasSize(5).containsOnly(escritorio);
			assertThat(tenantsDePermissao).containsExactly(escritorio);
		}
	}

	// ----------------------------------------------------------------- apoio

	private MvcTestResult cadastrar(String nomeEscritorio, String nome, String email, String senha) {
		String corpo = """
				{"nomeEscritorio": "%s", "nome": "%s", "email": "%s", "senha": "%s"}"""
				.formatted(nomeEscritorio, nome, email, senha);
		return mvc.post().uri(CADASTRO).contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
	}

	private UUID cadastrarValido(String email) {
		MvcTestResult resposta = cadastrar("Escritório " + email, "Proprietário", email, "senha-forte-123");
		assertThat(resposta).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(campo(resposta, "escritorioId"));
	}

	private static String campo(MvcTestResult resposta, String nome) {
		String json = corpo(resposta);
		var matcher = java.util.regex.Pattern.compile("\"" + nome + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		assertThat(matcher.find()).as("campo %s em %s", nome, json).isTrue();
		return matcher.group(1);
	}

	private static String corpo(MvcTestResult resposta) {
		try {
			return resposta.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException excecao) {
			throw new IllegalStateException(excecao);
		}
	}

	private <T> T noTenant(UUID tenant, Supplier<T> consulta) {
		return tenantContext.executarComo(tenant, null, () -> transacao.execute(status -> consulta.get()));
	}

	private static String emailUnico() {
		return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
	}

	/** Totais da plataforma inteira, lidos como superusuário (sem RLS). */
	private Totais totais() throws SQLException {
		try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); Statement sql = conexao.createStatement()) {
			return new Totais(contar(sql, "escritorio"), contar(sql, "usuario"), contar(sql, "grupo"),
					contar(sql, "grupo_permissao"));
		}
	}

	private static long contar(Statement sql, String tabela) throws SQLException {
		try (ResultSet resultado = sql.executeQuery("SELECT count(*) FROM " + tabela)) {
			resultado.next();
			return resultado.getLong(1);
		}
	}

	record Totais(long escritorios, long usuarios, long grupos, long permissoes) {
	}

}
