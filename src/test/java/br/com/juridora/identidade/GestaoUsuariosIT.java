package br.com.juridora.identidade;

import static br.com.juridora.ApoioApi.SENHA;
import static br.com.juridora.ApoioApi.campo;
import static br.com.juridora.ApoioApi.corpo;
import static br.com.juridora.ApoioApi.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HU08 – gestão de usuários pela API, com PostgreSQL real e a aplicação conectada como
 * {@code app_user} (RLS valendo). O plano Free permite 2 usuários ativos (RN09): o
 * proprietário e mais um.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GestaoUsuariosIT {

	@Autowired
	MockMvcTester mvc;

	ApoioApi api;

	Cadastro escritorio;

	String proprietario;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
		escritorio = api.cadastrarEscritorio();
		proprietario = api.token(escritorio.email());
	}

	// -------------------------------------------------------------- critério 1

	@Test
	void proprietarioCadastraUsuarioQueJaConsegueEntrar() {
		String email = emailUnico();
		UUID advogado = api.grupo(proprietario, "Advogado");

		MvcTestResult resposta = api.cadastrarUsuario(proprietario, email, advogado);

		assertThat(resposta).hasStatus(HttpStatus.CREATED);
		String id = campo(resposta, "$.id");
		assertThat(resposta.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/usuarios/" + id);
		assertThat(campo(resposta, "$.email")).isEqualTo(email);
		assertThat(campo(resposta, "$.grupoId")).isEqualTo(advogado.toString());
		assertThat(campo(resposta, "$.proprietario")).isEqualTo("false");
		assertThat(campo(resposta, "$.ativo")).isEqualTo("true");
		assertThat(corpo(resposta)).doesNotContain("senha");

		MvcTestResult perfil = api.perfil(api.token(email));
		assertThat(campo(perfil, "$.escritorioId")).isEqualTo(escritorio.escritorioId().toString());
		assertThat(corpo(perfil)).contains("USUARIO_VISUALIZAR").doesNotContain("USUARIO_GERENCIAR");
	}

	@Test
	void listaOsUsuariosDoEscritorioPaginados() {
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");

		MvcTestResult resposta = api.obter(proprietario, "/api/usuarios?page=0&size=10");

		assertThat(resposta).hasStatus(HttpStatus.OK);
		List<String> ids = JsonPath.read(corpo(resposta), "$.content[*].id");
		assertThat(ids).containsExactlyInAnyOrder(escritorio.usuarioId().toString(), membro.toString());
		assertThat(campo(resposta, "$.page.totalElements")).isEqualTo("2");
		assertThat(api.obter(proprietario, "/api/usuarios/" + membro)).hasStatus(HttpStatus.OK);
	}

	@Test
	void dadosInvalidosRespondem400ComOsCampos() {
		MvcTestResult resposta = api.postar(proprietario, "/api/usuarios", """
				{"nome": "", "email": "invalido", "senha": "curta"}""");

		assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
		List<String> campos = JsonPath.read(corpo(resposta), "$.campos[*].campo");
		assertThat(campos).contains("nome", "email", "senha", "grupoId");
	}

	@Test
	void grupoDeOutroEscritorioNaoServe() {
		Cadastro outro = api.cadastrarEscritorio();
		UUID grupoDoOutro = api.grupo(api.token(outro.email()), "Advogado");

		MvcTestResult resposta = api.cadastrarUsuario(proprietario, emailUnico(), grupoDoOutro);

		assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(campo(resposta, "$.detail")).contains("grupo");
	}

	@Test
	void emailJaCadastradoNaPlataformaResponde409() {
		Cadastro outro = api.cadastrarEscritorio();

		MvcTestResult resposta = api.cadastrarUsuario(proprietario, outro.email().toUpperCase(),
				api.grupo(proprietario, "Usuário"));

		assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/email-ja-cadastrado");
	}

	// -------------------------------------------------------------- critério 2

	@Test
	void limiteDoPlanoBloqueiaCadastroEReativacao() {
		UUID primeiro = api.membro(proprietario, emailUnico(), "Advogado");

		MvcTestResult acimaDoLimite = api.cadastrarUsuario(proprietario, emailUnico(), api.grupo(proprietario, "Advogado"));

		assertThat(acimaDoLimite).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(acimaDoLimite, "$.type")).isEqualTo("/problemas/limite-do-plano");

		// Desativar libera a vaga; reativar o primeiro, com a vaga ocupada, é bloqueado.
		assertThat(api.acionar(proprietario, "/api/usuarios/" + primeiro + "/desativar")).hasStatus(HttpStatus.NO_CONTENT);
		api.membro(proprietario, emailUnico(), "Estagiário");
		MvcTestResult reativar = api.acionar(proprietario, "/api/usuarios/" + primeiro + "/reativar");
		assertThat(reativar).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(reativar, "$.type")).isEqualTo("/problemas/limite-do-plano");
	}

	// -------------------------------------------------------------- critério 3

	@Test
	void editaNomeEEmailEOLoginPassaASerONovoEmail() {
		String emailAntigo = emailUnico();
		UUID membro = api.membro(proprietario, emailAntigo, "Advogado");
		String emailNovo = emailUnico();

		MvcTestResult resposta = api.alterar(proprietario, "/api/usuarios/" + membro, """
				{"nome": "Nome Novo", "email": "%s"}""".formatted(emailNovo));

		assertThat(resposta).hasStatus(HttpStatus.OK);
		assertThat(campo(resposta, "$.nome")).isEqualTo("Nome Novo");
		assertThat(api.login(emailAntigo, SENHA)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(api.login(emailNovo, SENHA)).hasStatus(HttpStatus.OK);
	}

	@Test
	void editarParaEmailJaUsadoResponde409() {
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");

		MvcTestResult resposta = api.alterar(proprietario, "/api/usuarios/" + membro, """
				{"nome": "Membro", "email": "%s"}""".formatted(escritorio.email()));

		assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
	}

	// -------------------------------------------------------------- critério 4

	@Test
	void desativarDerrubaOsTokensEImpedeLoginEReativarDevolveOAcesso() {
		String email = emailUnico();
		UUID membro = api.membro(proprietario, email, "Advogado");
		String tokenAntigo = api.token(email);
		assertThat(api.perfil(tokenAntigo)).hasStatus(HttpStatus.OK);
		// O iat tem precisão de segundos: desativar em outro segundo deixa a última verificação determinística.
		aguardarProximoSegundo();

		assertThat(api.acionar(proprietario, "/api/usuarios/" + membro + "/desativar")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(api.perfil(tokenAntigo)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(api.obter(tokenAntigo, "/api/usuarios")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(api.login(email, SENHA)).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult desativado = api.obter(proprietario, "/api/usuarios/" + membro);
		assertThat(campo(desativado, "$.ativo")).isEqualTo("false");

		assertThat(api.acionar(proprietario, "/api/usuarios/" + membro + "/reativar")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(api.perfil(api.token(email))).hasStatus(HttpStatus.OK);
		assertThat(api.perfil(tokenAntigo)).as("token de antes da desativação continua inválido")
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void desativarDuasVezesNaoEhErro() {
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");
		api.acionar(proprietario, "/api/usuarios/" + membro + "/desativar");

		assertThat(api.acionar(proprietario, "/api/usuarios/" + membro + "/desativar")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void proprietarioNaoPodeSerDesativado() {
		MvcTestResult resposta = api.acionar(proprietario, "/api/usuarios/" + escritorio.usuarioId() + "/desativar");

		assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/proprietario-nao-desativavel");
		assertThat(api.perfil(proprietario)).hasStatus(HttpStatus.OK);
	}

	// -------------------------------------------------------------- critério 5

	@Test
	void semUsuarioGerenciarSoConsulta() {
		String email = emailUnico();
		api.membro(proprietario, email, "Advogado");
		String advogado = api.token(email);

		assertThat(api.obter(advogado, "/api/usuarios")).hasStatus(HttpStatus.OK);
		assertThat(api.obter(advogado, "/api/usuarios/" + escritorio.usuarioId())).hasStatus(HttpStatus.OK);
		MvcTestResult cadastrar = api.cadastrarUsuario(advogado, emailUnico(), UUID.randomUUID());
		assertThat(cadastrar).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(campo(cadastrar, "$.type")).isEqualTo("/problemas/sem-permissao");
		assertThat(api.alterar(advogado, "/api/usuarios/" + escritorio.usuarioId(), """
				{"nome": "Invasor", "email": "x@exemplo.com"}""")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.acionar(advogado, "/api/usuarios/" + escritorio.usuarioId() + "/desativar"))
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.obter(advogado, "/api/grupos")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void semTokenResponde401() {
		assertThat(mvc.get().uri("/api/usuarios").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/grupos").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void usuarioDeOutroEscritorioResponde404EmTodasAsOperacoes() {
		Cadastro outro = api.cadastrarEscritorio();
		String tokenOutro = api.token(outro.email());
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");

		for (MvcTestResult resposta : List.of(
				api.obter(tokenOutro, "/api/usuarios/" + membro),
				api.alterar(tokenOutro, "/api/usuarios/" + membro, """
						{"nome": "Invasor", "email": "%s"}""".formatted(emailUnico())),
				api.acionar(tokenOutro, "/api/usuarios/" + membro + "/desativar"),
				api.acionar(tokenOutro, "/api/usuarios/" + membro + "/reativar"))) {
			assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/usuario-nao-encontrado");
		}
		List<String> idsVistosPeloOutro = JsonPath.read(corpo(api.obter(tokenOutro, "/api/usuarios")), "$.content[*].id");
		assertThat(idsVistosPeloOutro).containsExactly(outro.usuarioId().toString());
		assertThat(campo(api.obter(proprietario, "/api/usuarios/" + membro), "$.ativo")).isEqualTo("true");
	}

	private static void aguardarProximoSegundo() {
		try {
			Thread.sleep(1_050 - System.currentTimeMillis() % 1_000);
		}
		catch (InterruptedException excecao) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(excecao);
		}
	}

	@Test
	void listaGruposDoEscritorio() {
		MvcTestResult resposta = api.obter(proprietario, "/api/grupos");

		assertThat(resposta).hasStatus(HttpStatus.OK);
		List<String> nomes = JsonPath.read(corpo(resposta), "$[*].nome");
		assertThat(nomes).containsExactly("Advogado", "Estagiário", "Gerente", "Proprietário", "Usuário");
	}

}
