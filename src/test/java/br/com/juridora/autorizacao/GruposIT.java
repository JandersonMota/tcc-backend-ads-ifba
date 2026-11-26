package br.com.juridora.autorizacao;

import static br.com.juridora.ApoioApi.campo;
import static br.com.juridora.ApoioApi.corpo;
import static br.com.juridora.ApoioApi.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.juridora.ApoioApi;
import br.com.juridora.ApoioApi.Cadastro;
import br.com.juridora.TestcontainersConfiguration;
import br.com.juridora.autorizacao.dominio.modelo.Permissao;
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
 * HU09 – grupos personalizados e permissões dos grupos pela API, com PostgreSQL real e a
 * aplicação conectada como {@code app_user} (RLS valendo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GruposIT {

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
	void proprietarioCriaGrupoEQuemEntraNeleRecebeAsPermissoes() {
		MvcTestResult resposta = api.criarGrupo(proprietario, "Financeiro", "FINANCEIRO_VISUALIZAR",
				"CLIENTE_VISUALIZAR");

		assertThat(resposta).hasStatus(HttpStatus.CREATED);
		String id = campo(resposta, "$.id");
		assertThat(resposta.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/grupos/" + id);
		assertThat(campo(resposta, "$.padrao")).isEqualTo("false");
		List<String> permissoes = JsonPath.read(corpo(resposta), "$.permissoes");
		assertThat(permissoes).containsExactly("CLIENTE_VISUALIZAR", "FINANCEIRO_VISUALIZAR");

		String email = emailUnico();
		api.membro(proprietario, email, "Financeiro");
		List<String> doPerfil = JsonPath.read(corpo(api.perfil(api.token(email))), "$.permissoes");
		assertThat(doPerfil).containsExactlyInAnyOrder("CLIENTE_VISUALIZAR", "FINANCEIRO_VISUALIZAR");
	}

	@Test
	void grupoPodeNascerSemPermissoesEApareceNaListagem() {
		UUID id = api.grupoPersonalizado(proprietario, "Recepção");

		assertThat(JsonPath.<List<String>>read(corpo(api.obter(proprietario, "/api/grupos/" + id)), "$.permissoes"))
				.isEmpty();
		assertThat(api.grupo(proprietario, "Recepção")).isEqualTo(id);
	}

	@Test
	void nomeRepetidoNoEscritorioResponde409SemDiferenciarMaiusculas() {
		api.grupoPersonalizado(proprietario, "Financeiro");

		MvcTestResult repetido = api.criarGrupo(proprietario, "  FINANCEIRO ");
		MvcTestResult padrao = api.criarGrupo(proprietario, "gerente");

		assertThat(repetido).hasStatus(HttpStatus.CONFLICT);
		assertThat(campo(repetido, "$.type")).isEqualTo("/problemas/grupo-ja-existe");
		assertThat(padrao).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void mesmoNomeEmOutroEscritorioEPermitido() {
		api.grupoPersonalizado(proprietario, "Financeiro");
		String outro = api.token(api.cadastrarEscritorio().email());

		assertThat(api.criarGrupo(outro, "Financeiro")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void dadosInvalidosRespondem400() {
		MvcTestResult semNome = api.postar(proprietario, "/api/grupos", """
				{"nome": " ", "permissoes": [""]}""");
		MvcTestResult foraDoCatalogo = api.criarGrupo(proprietario, "Financeiro", "FINANCEIRO_APAGAR_TUDO");

		assertThat(semNome).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<List<String>>read(corpo(semNome), "$.campos[*].campo")).contains("nome");
		assertThat(foraDoCatalogo).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(campo(foraDoCatalogo, "$.detail")).contains("FINANCEIRO_APAGAR_TUDO");
		assertThat(api.obter(proprietario, "/api/grupos")).bodyText().doesNotContain("Financeiro");
	}

	@Test
	void catalogoListaTodasAsPermissoes() {
		MvcTestResult resposta = api.obter(proprietario, "/api/permissoes");

		assertThat(resposta).hasStatus(HttpStatus.OK);
		assertThat(JsonPath.<List<String>>read(corpo(resposta), "$"))
				.containsExactlyElementsOf(Arrays.stream(Permissao.values()).map(Permissao::name).toList());
	}

	// -------------------------------------------------------------- critério 2

	@Test
	void editarPermissoesDeGrupoPadraoValeNoProximoLogin() {
		String email = emailUnico();
		api.membro(proprietario, email, "Estagiário");
		String tokenAntigo = api.token(email);
		UUID estagiario = api.grupo(proprietario, "Estagiário");

		MvcTestResult resposta = api.alterar(proprietario, "/api/grupos/" + estagiario + "/permissoes", """
				{"permissoes": ["CLIENTE_VISUALIZAR", "FINANCEIRO_VISUALIZAR"]}""");

		assertThat(resposta).hasStatus(HttpStatus.OK);
		assertThat(campo(resposta, "$.nome")).isEqualTo("Estagiário");
		assertThat(JsonPath.<List<String>>read(corpo(resposta), "$.permissoes"))
				.containsExactly("CLIENTE_VISUALIZAR", "FINANCEIRO_VISUALIZAR");
		// O token já emitido continua com as permissões antigas até expirar (ADR-014).
		assertThat(api.perfil(tokenAntigo)).hasStatus(HttpStatus.OK);
		List<String> doNovoLogin = JsonPath.read(corpo(api.perfil(api.token(email))), "$.permissoes");
		assertThat(doNovoLogin).containsExactly("CLIENTE_VISUALIZAR", "FINANCEIRO_VISUALIZAR");
	}

	@Test
	void listaVaziaRetiraTodasAsPermissoesENuloE400() {
		UUID id = api.grupoPersonalizado(proprietario, "Financeiro", "FINANCEIRO_VISUALIZAR");

		MvcTestResult nulo = api.alterar(proprietario, "/api/grupos/" + id + "/permissoes", "{}");
		MvcTestResult vazio = api.alterar(proprietario, "/api/grupos/" + id + "/permissoes", """
				{"permissoes": []}""");

		assertThat(nulo).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(vazio).hasStatus(HttpStatus.OK);
		assertThat(JsonPath.<List<String>>read(corpo(vazio), "$.permissoes")).isEmpty();
	}

	@Test
	void permissoesDoGrupoProprietarioNaoMudam() {
		UUID grupoProprietario = api.grupo(proprietario, "Proprietário");

		MvcTestResult resposta = api.alterar(proprietario, "/api/grupos/" + grupoProprietario + "/permissoes", """
				{"permissoes": ["CLIENTE_VISUALIZAR"]}""");

		assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/grupo-proprietario-protegido");
		assertThat(JsonPath.<List<String>>read(corpo(api.obter(proprietario, "/api/grupos/" + grupoProprietario)),
				"$.permissoes")).hasSize(Permissao.values().length);
	}

	@Test
	void renomeiaPersonalizadoEPadraoMenosOProprietario() {
		UUID id = api.grupoPersonalizado(proprietario, "Financeiro");
		api.grupoPersonalizado(proprietario, "Recepção");

		MvcTestResult renomeado = api.alterar(proprietario, "/api/grupos/" + id, """
				{"nome": "Cobrança"}""");
		MvcTestResult repetido = api.alterar(proprietario, "/api/grupos/" + id, """
				{"nome": "recepção"}""");
		MvcTestResult padrao = api.alterar(proprietario, "/api/grupos/" + api.grupo(proprietario, "Advogado"), """
				{"nome": "Advogados"}""");
		// Outro grupo padrão também não assume o nome do Proprietário.
		MvcTestResult comoProprietario = api.alterar(proprietario,
				"/api/grupos/" + api.grupo(proprietario, "Gerente"), """
				{"nome": "proprietário"}""");
		MvcTestResult donoDoEscritorio = api.alterar(proprietario,
				"/api/grupos/" + api.grupo(proprietario, "Proprietário"), """
				{"nome": "Sócios"}""");

		assertThat(renomeado).hasStatus(HttpStatus.OK);
		assertThat(campo(renomeado, "$.nome")).isEqualTo("Cobrança");
		assertThat(repetido).hasStatus(HttpStatus.CONFLICT);
		assertThat(padrao).hasStatus(HttpStatus.OK);
		assertThat(campo(padrao, "$.nome")).isEqualTo("Advogados");
		assertThat(campo(padrao, "$.padrao")).isEqualTo("true");
		assertThat(comoProprietario).hasStatus(HttpStatus.CONFLICT);
		assertThat(donoDoEscritorio).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(donoDoEscritorio, "$.type")).isEqualTo("/problemas/grupo-proprietario-protegido");
	}

	// -------------------------------------------------------------- critério 3

	@Test
	void excluiGrupoPersonalizadoSemUsuarios() {
		UUID id = api.grupoPersonalizado(proprietario, "Temporário", "CLIENTE_VISUALIZAR");

		assertThat(api.excluir(proprietario, "/api/grupos/" + id)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(api.obter(proprietario, "/api/grupos/" + id)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(api.excluir(proprietario, "/api/grupos/" + id)).hasStatus(HttpStatus.NOT_FOUND);
		// O grupo excluído não pode mais receber usuários.
		assertThat(api.cadastrarUsuario(proprietario, emailUnico(), id)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void grupoComUsuarioMesmoDesativadoNaoEExcluido() {
		UUID id = api.grupoPersonalizado(proprietario, "Financeiro");
		UUID membro = api.membro(proprietario, emailUnico(), "Financeiro");
		assertThat(api.acionar(proprietario, "/api/usuarios/" + membro + "/desativar")).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult resposta = api.excluir(proprietario, "/api/grupos/" + id);

		assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/grupo-com-usuarios");
		assertThat(api.obter(proprietario, "/api/grupos/" + id)).hasStatus(HttpStatus.OK);
	}

	@Test
	void grupoPadraoSemUsuariosEExcluidoMasComUsuariosNao() {
		UUID usuario = api.grupo(proprietario, "Usuário");
		UUID advogado = api.grupo(proprietario, "Advogado");
		api.membro(proprietario, emailUnico(), "Advogado");

		assertThat(api.excluir(proprietario, "/api/grupos/" + usuario)).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult comUsuario = api.excluir(proprietario, "/api/grupos/" + advogado);

		assertThat(api.obter(proprietario, "/api/grupos/" + usuario)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(comUsuario).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(comUsuario, "$.type")).isEqualTo("/problemas/grupo-com-usuarios");
	}

	@Test
	void grupoProprietarioNaoEExcluido() {
		MvcTestResult resposta = api.excluir(proprietario, "/api/grupos/" + api.grupo(proprietario, "Proprietário"));

		assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/grupo-proprietario-protegido");
	}

	// -------------------------------------------------------------- critério 4

	@Test
	void usuarioComTodasAsPermissoesQueNaoEProprietarioRecebe403() {
		UUID gerente = api.grupo(proprietario, "Gerente");
		String todas = Arrays.stream(Permissao.values()).map(p -> "\"" + p.name() + "\"")
				.reduce((a, b) -> a + ", " + b).orElseThrow();
		assertThat(api.alterar(proprietario, "/api/grupos/" + gerente + "/permissoes",
				"{\"permissoes\": [" + todas + "]}")).hasStatus(HttpStatus.OK);
		String email = emailUnico();
		api.membro(proprietario, email, "Gerente");
		String naoProprietario = api.token(email);
		UUID personalizado = api.grupoPersonalizado(proprietario, "Financeiro");

		// Com USUARIO_GERENCIAR, lista os grupos para atribuí-los (HU08)...
		assertThat(api.obter(naoProprietario, "/api/grupos")).hasStatus(HttpStatus.OK);
		// ...mas não os gerencia (RN04, RN05).
		List<MvcTestResult> exclusivas = List.of(
				api.obter(naoProprietario, "/api/permissoes"),
				api.obter(naoProprietario, "/api/grupos/" + personalizado),
				api.criarGrupo(naoProprietario, "Outro"),
				api.alterar(naoProprietario, "/api/grupos/" + personalizado, "{\"nome\": \"Outro\"}"),
				api.alterar(naoProprietario, "/api/grupos/" + gerente + "/permissoes", "{\"permissoes\": []}"),
				api.excluir(naoProprietario, "/api/grupos/" + personalizado));
		assertThat(exclusivas).allSatisfy(resposta -> {
			assertThat(resposta).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/sem-permissao");
		});
	}

	@Test
	void semTokenResponde401() {
		assertThat(mvc.post().uri("/api/grupos").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/permissoes").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void grupoDeOutroEscritorioResponde404EmTodasAsOperacoes() {
		String outro = api.token(api.cadastrarEscritorio().email());
		UUID grupoDoOutro = api.grupoPersonalizado(outro, "Financeiro", "FINANCEIRO_VISUALIZAR");
		String uri = "/api/grupos/" + grupoDoOutro;

		List<MvcTestResult> respostas = List.of(
				api.obter(proprietario, uri),
				api.alterar(proprietario, uri, "{\"nome\": \"Invadido\"}"),
				api.alterar(proprietario, uri + "/permissoes", "{\"permissoes\": []}"),
				api.excluir(proprietario, uri));

		assertThat(respostas).allSatisfy(resposta -> {
			assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/grupo-nao-encontrado");
		});
		MvcTestResult doOutro = api.obter(outro, uri);
		assertThat(campo(doOutro, "$.nome")).isEqualTo("Financeiro");
		assertThat(JsonPath.<List<String>>read(corpo(doOutro), "$.permissoes")).containsExactly("FINANCEIRO_VISUALIZAR");
		assertThat(api.obter(proprietario, "/api/grupos")).bodyText().doesNotContain(grupoDoOutro.toString());
	}

}
