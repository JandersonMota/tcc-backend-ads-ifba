package br.com.juridora.identidade;

import static br.com.juridora.ApoioApi.campo;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HU07 – logout pela API, com PostgreSQL real e a aplicação conectada como
 * {@code app_user} (RLS valendo). O token encerrado deixa de valer em qualquer rota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogoutIT {

	@Autowired
	MockMvcTester mvc;

	ApoioApi api;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
	}

	@Test
	void sairResponde204EOTokenDeixaDeValer() {
		Cadastro cadastro = api.cadastrarEscritorio();
		String token = api.token(cadastro.email());
		assertThat(api.perfil(token)).hasStatus(HttpStatus.OK);

		MvcTestResult saida = api.sair(token);

		assertThat(saida).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult depois = api.perfil(token);
		assertThat(depois).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(depois).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(campo(depois, "$.type")).isEqualTo("/problemas/nao-autenticado");
	}

	@Test
	void tokenEncerradoNaoSaiDeNovo() {
		Cadastro cadastro = api.cadastrarEscritorio();
		String token = api.token(cadastro.email());
		api.sair(token);

		assertThat(api.sair(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void sairSemTokenResponde401() {
		assertThat(mvc.post().uri("/api/autenticacao/sair").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void soOTokenDaChamadaEhEncerrado() {
		Cadastro cadastro = api.cadastrarEscritorio();
		String noNotebook = api.token(cadastro.email());
		String noCelular = api.token(cadastro.email());

		api.sair(noNotebook);

		assertThat(api.perfil(noNotebook)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(api.perfil(noCelular)).hasStatus(HttpStatus.OK);
	}

	@Test
	void novoLoginDepoisDeSairFunciona() {
		Cadastro cadastro = api.cadastrarEscritorio();
		api.sair(api.token(cadastro.email()));

		assertThat(api.perfil(api.token(cadastro.email()))).hasStatus(HttpStatus.OK);
	}

	@Test
	void logoutDeUmEscritorioNaoAfetaOutro() {
		Cadastro escritorioA = api.cadastrarEscritorio();
		Cadastro escritorioB = api.cadastrarEscritorio();
		String tokenA = api.token(escritorioA.email());
		String tokenB = api.token(escritorioB.email());

		api.sair(tokenA);

		assertThat(api.perfil(tokenA)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(api.perfil(tokenB)).hasStatus(HttpStatus.OK);
	}

}
