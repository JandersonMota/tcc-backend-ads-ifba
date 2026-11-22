package br.com.juridora.identidade;

import static br.com.juridora.ApoioApi.SENHA;
import static br.com.juridora.ApoioApi.campo;
import static br.com.juridora.ApoioApi.corpo;
import static br.com.juridora.ApoioApi.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU06 – login pela API, com PostgreSQL real e a aplicação conectada como
 * {@code app_user} (RLS valendo). Casos de teste CT15 a CT18.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	PostgreSQLContainer postgres;

	ApoioApi api;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
	}

	@Nested
	class CredenciaisValidas {

		@Test
		void devolveTokenComOTenantEOUsuario() {
			Cadastro cadastro = api.cadastrarEscritorio();

			MvcTestResult resposta = api.login(cadastro.email(), SENHA);

			assertThat(resposta).hasStatus(HttpStatus.OK);
			assertThat(campo(resposta, "$.tipo")).isEqualTo("Bearer");
			assertThat(campo(resposta, "$.expiraEm")).isEqualTo("900");

			Jwt jwt = jwtDecoder.decode(campo(resposta, "$.token"));
			assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(cadastro.escritorioId().toString());
			assertThat(jwt.getSubject()).isEqualTo(cadastro.usuarioId().toString());
			assertThat(jwt.getClaimAsString("iss")).isEqualTo("juridora");
			assertThat(jwt.getId()).isNotBlank();
			assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		}

		@Test
		void tokenTrazAsPermissoesDoGrupoProprietario() {
			Cadastro cadastro = api.cadastrarEscritorio();

			Jwt jwt = jwtDecoder.decode(api.token(cadastro.email()));

			assertThat(jwt.getClaimAsBoolean("proprietario")).isTrue();
			assertThat(jwt.getClaimAsStringList("permissoes")).hasSize(22)
					.contains("PROCESSO_CRIAR", "USUARIO_GERENCIAR", "CLIENTE_VISUALIZAR");
		}

		@Test
		void emailComMaiusculasEhOMesmoLogin() {
			Cadastro cadastro = api.cadastrarEscritorio();

			MvcTestResult resposta = api.login(cadastro.email().toUpperCase(), SENHA);

			assertThat(resposta).hasStatus(HttpStatus.OK);
		}

		@Test
		void corpoDaRespostaNaoTrazASenha() {
			Cadastro cadastro = api.cadastrarEscritorio();

			MvcTestResult resposta = api.login(cadastro.email(), SENHA);

			assertThat(corpo(resposta)).doesNotContain(SENHA);
		}

	}

	@Nested
	class CredenciaisInvalidas {

		@Test
		void senhaErradaEmailInexistenteEUsuarioDesativadoTemAMesmaResposta() throws SQLException {
			Cadastro comSenhaErrada = api.cadastrarEscritorio();
			Cadastro desativado = api.cadastrarEscritorio();
			desativar(desativado.usuarioId());

			MvcTestResult senhaErrada = api.login(comSenhaErrada.email(), "senha-errada-123");
			MvcTestResult emailInexistente = api.login(emailUnico(), SENHA);
			MvcTestResult usuarioDesativado = api.login(desativado.email(), SENHA);

			for (MvcTestResult resposta : List.of(senhaErrada, emailInexistente, usuarioDesativado)) {
				assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
				assertThat(resposta).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
				assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/credenciais-invalidas");
				assertThat(campo(resposta, "$.detail")).isEqualTo("E-mail ou senha inválidos.");
			}
			assertThat(corpo(emailInexistente)).isEqualTo(corpo(senhaErrada));
			assertThat(corpo(usuarioDesativado)).isEqualTo(corpo(senhaErrada));
		}

		@Test
		void senhaComMaisDe72BytesEhRecusadaSemErroInterno() {
			Cadastro cadastro = api.cadastrarEscritorio();

			MvcTestResult resposta = api.login(cadastro.email(), "a".repeat(100));

			assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"""
				{"email": "", "senha": "senha-forte-123"}""",
				"""
				{"email": "nao-e-email", "senha": "senha-forte-123"}""",
				"""
				{"email": "ana@x.com", "senha": ""}""",
				"{}" })
		void corpoInvalidoRetorna400(String corpo) {
			MvcTestResult resposta = api.postar("/api/autenticacao/login", corpo);

			assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(resposta).bodyJson().extractingPath("$.campos").asArray().isNotEmpty();
		}

	}

	@Nested
	class TentativasRepetidas {

		@Test
		void cincoFalhasBloqueiamAteComASenhaCerta() {
			Cadastro cadastro = api.cadastrarEscritorio();
			for (int i = 0; i < 5; i++) {
				assertThat(api.login(cadastro.email(), "senha-errada-123")).hasStatus(HttpStatus.UNAUTHORIZED);
			}

			MvcTestResult resposta = api.login(cadastro.email(), SENHA);

			assertThat(resposta).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/muitas-tentativas");
			long retryAfter = Long.parseLong(resposta.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
			assertThat(retryAfter).isBetween(1L, 900L);
		}

		@Test
		void emailInexistenteTambemEhBloqueado() {
			String email = emailUnico();
			for (int i = 0; i < 5; i++) {
				assertThat(api.login(email, SENHA)).hasStatus(HttpStatus.UNAUTHORIZED);
			}

			assertThat(api.login(email, SENHA)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		}

		@Test
		void loginCertoAntesDoLimiteZeraAsFalhas() {
			Cadastro cadastro = api.cadastrarEscritorio();
			for (int i = 0; i < 4; i++) {
				api.login(cadastro.email(), "senha-errada-123");
			}
			assertThat(api.login(cadastro.email(), SENHA)).hasStatus(HttpStatus.OK);

			for (int i = 0; i < 4; i++) {
				api.login(cadastro.email(), "senha-errada-123");
			}

			assertThat(api.login(cadastro.email(), SENHA)).hasStatus(HttpStatus.OK);
		}

	}

	@Nested
	class Perfil {

		@Test
		void cadaTokenSoEnxergaOProprioEscritorio() {
			Cadastro escritorioA = api.cadastrarEscritorio();
			Cadastro escritorioB = api.cadastrarEscritorio();

			for (Cadastro cadastro : List.of(escritorioA, escritorioB)) {
				MvcTestResult resposta = api.perfil(api.token(cadastro.email()));

				assertThat(resposta).hasStatus(HttpStatus.OK);
				assertThat(campo(resposta, "$.id")).isEqualTo(cadastro.usuarioId().toString());
				assertThat(campo(resposta, "$.escritorioId")).isEqualTo(cadastro.escritorioId().toString());
				assertThat(campo(resposta, "$.email")).isEqualTo(cadastro.email());
				assertThat(campo(resposta, "$.proprietario")).isEqualTo("true");
				assertThat(resposta).bodyJson().extractingPath("$.permissoes").asArray().hasSize(22);
			}
		}

		@Test
		void usuarioDesativadoDepoisDoLoginPerdeOAcesso() throws SQLException {
			Cadastro cadastro = api.cadastrarEscritorio();
			String token = api.token(cadastro.email());
			desativar(cadastro.usuarioId());

			MvcTestResult resposta = api.perfil(token);

			assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
		}

	}

	// ----------------------------------------------------------------- apoio

	/** Desativa o usuário como superusuário do contêiner (ainda não há API para isso, HU08). */
	private void desativar(UUID usuarioId) throws SQLException {
		try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
				PreparedStatement sql = conexao.prepareStatement("UPDATE usuario SET ativo = false WHERE id = ?")) {
			sql.setObject(1, usuarioId);
			assertThat(sql.executeUpdate()).isEqualTo(1);
		}
	}

}
