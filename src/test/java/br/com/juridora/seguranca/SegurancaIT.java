package br.com.juridora.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HU06 – Spring Security nega por padrão e só aceita o JWT emitido pela própria
 * aplicação (ADR-014). Caso de teste CT19. O tenant da requisição vem só do token
 * validado (RNF02).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SegurancaIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JwtEncoder encoderDaAplicacao;

	ApoioApi api;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
	}

	@Test
	void semTokenResponde401EmProblemDetails() {
		MvcTestResult resposta = mvc.get().uri("/api/perfil").exchange();

		assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resposta).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(resposta).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		assertThat(ApoioApi.campo(resposta, "$.type")).isEqualTo("/problemas/nao-autenticado");
	}

	@Test
	void rotaSemRegraExplicitaEhNegadaPorPadrao() {
		assertThat(mvc.get().uri("/api/qualquer-coisa").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/actuator/modulith").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rotasPublicasNaoExigemToken() {
		assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatus(HttpStatus.OK);
		assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatus(HttpStatus.OK);
		assertThat(api.login(ApoioApi.emailUnico(), ApoioApi.SENHA)).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson().extractingPath("$.type").isEqualTo("/problemas/credenciais-invalidas");
	}

	@Test
	void tokenValidoDaAcesso() {
		Cadastro cadastro = api.cadastrarEscritorio();

		assertThat(api.perfil(api.token(cadastro.email()))).hasStatus(HttpStatus.OK);
	}

	@Test
	void tokenAssinadoComOutraChaveEhRecusado() {
		Cadastro cadastro = api.cadastrarEscritorio();
		JwtEncoder outraChave = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(chaveRsaNova())));

		String token = emitir(outraChave, cadastro, _ -> {
		});

		assertThat(api.perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenExpiradoEhRecusado() {
		Cadastro cadastro = api.cadastrarEscritorio();
		Instant passado = Instant.now().minus(Duration.ofHours(1));

		String token = emitir(encoderDaAplicacao, cadastro, claims -> claims.issuedAt(passado)
				.expiresAt(passado.plus(Duration.ofMinutes(15))));

		assertThat(api.perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenDeOutroEmissorEhRecusado() {
		Cadastro cadastro = api.cadastrarEscritorio();

		String token = emitir(encoderDaAplicacao, cadastro, claims -> claims.issuer("outro-emissor"));

		assertThat(api.perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenSemTenantEhRecusado() {
		Cadastro cadastro = api.cadastrarEscritorio();

		String token = emitir(encoderDaAplicacao, cadastro, claims -> claims.claims(mapa -> mapa.remove("tenant_id")));

		assertThat(api.perfil(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void trocarOTenantNoTokenInvalidaAAssinatura() {
		Cadastro vitima = api.cadastrarEscritorio();
		Cadastro atacante = api.cadastrarEscritorio();
		String token = api.token(atacante.email());

		String[] partes = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(partes[1]))
				.replace(atacante.escritorioId().toString(), vitima.escritorioId().toString());
		String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
				+ "." + partes[2];

		assertThat(api.perfil(adulterado)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ----------------------------------------------------------------- apoio

	private static String emitir(JwtEncoder encoder, Cadastro cadastro, Consumer<JwtClaimsSet.Builder> ajuste) {
		Instant agora = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer("juridora")
				.subject(cadastro.usuarioId().toString())
				.issuedAt(agora)
				.expiresAt(agora.plus(Duration.ofMinutes(15)))
				.id(UUID.randomUUID().toString())
				.claim("tenant_id", cadastro.escritorioId().toString())
				.claim("proprietario", true)
				.claim("permissoes", List.of());
		ajuste.accept(claims);
		JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(cabecalho, claims.build())).getTokenValue();
	}

	private static RSAKey chaveRsaNova() {
		try {
			KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
			gerador.initialize(2048);
			KeyPair par = gerador.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) par.getPublic()).privateKey((RSAPrivateKey) par.getPrivate())
					.build();
		}
		catch (NoSuchAlgorithmException excecao) {
			throw new IllegalStateException(excecao);
		}
	}

}
