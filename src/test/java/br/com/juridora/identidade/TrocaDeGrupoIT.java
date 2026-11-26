package br.com.juridora.identidade;

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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HU10 – troca do grupo de um usuário pela API, com PostgreSQL real e RLS valendo. O plano
 * Free permite 2 usuários ativos (RN09): o proprietário e mais um.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrocaDeGrupoIT {

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
	void trocaDeGrupoValeNoProximoLogin() {
		String email = emailUnico();
		UUID membro = api.membro(proprietario, email, "Advogado");
		UUID estagiario = api.grupo(proprietario, "Estagiário");

		MvcTestResult resposta = api.trocarGrupo(proprietario, membro, estagiario);

		assertThat(resposta).hasStatus(HttpStatus.OK);
		assertThat(campo(resposta, "$.grupoId")).isEqualTo(estagiario.toString());
		List<String> doGrupo = JsonPath.read(corpo(api.obter(proprietario, "/api/grupos/" + estagiario)),
				"$.permissoes");
		List<String> doPerfil = JsonPath.read(corpo(api.perfil(api.token(email))), "$.permissoes");
		assertThat(doPerfil).containsExactlyInAnyOrderElementsOf(doGrupo);
	}

	@Test
	void trocarParaOGrupoAtualNaoEErro() {
		UUID advogado = api.grupo(proprietario, "Advogado");
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");

		MvcTestResult resposta = api.trocarGrupo(proprietario, membro, advogado);

		assertThat(resposta).hasStatus(HttpStatus.OK);
		assertThat(campo(resposta, "$.grupoId")).isEqualTo(advogado.toString());
	}

	@Test
	void grupoQueFicouSemUsuariosPodeSerExcluido() {
		UUID financeiro = api.grupoPersonalizado(proprietario, "Financeiro", "FINANCEIRO_VISUALIZAR");
		UUID membro = UUID.fromString(campo(api.cadastrarUsuario(proprietario, emailUnico(), financeiro), "$.id"));
		assertThat(api.excluir(proprietario, "/api/grupos/" + financeiro)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

		assertThat(api.trocarGrupo(proprietario, membro, api.grupo(proprietario, "Usuário"))).hasStatus(HttpStatus.OK);

		assertThat(api.excluir(proprietario, "/api/grupos/" + financeiro)).hasStatus(HttpStatus.NO_CONTENT);
	}

	// -------------------------------------------------------------- critério 2

	@Test
	void grupoDeOutroEscritorioOuAusenteResponde400() {
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");
		UUID grupoDoOutro = api.grupo(api.token(api.cadastrarEscritorio().email()), "Advogado");

		MvcTestResult deOutro = api.trocarGrupo(proprietario, membro, grupoDoOutro);
		MvcTestResult ausente = api.alterar(proprietario, "/api/usuarios/" + membro + "/grupo", "{}");

		assertThat(deOutro).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(campo(deOutro, "$.detail")).contains("grupo");
		assertThat(ausente).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void usuarioDeOutroEscritorioResponde404() {
		Cadastro outro = api.cadastrarEscritorio();

		MvcTestResult resposta = api.trocarGrupo(proprietario, outro.usuarioId(), api.grupo(proprietario, "Advogado"));

		assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND);
	}

	// -------------------------------------------------------------- critério 3

	@Test
	void grupoProprietarioEReservadoAoDono() {
		UUID grupoProprietario = api.grupo(proprietario, "Proprietário");
		UUID membro = api.membro(proprietario, emailUnico(), "Advogado");

		List<MvcTestResult> respostas = List.of(
				api.trocarGrupo(proprietario, membro, grupoProprietario),
				api.trocarGrupo(proprietario, escritorio.usuarioId(), api.grupo(proprietario, "Advogado")),
				api.cadastrarUsuario(proprietario, emailUnico(), grupoProprietario));

		assertThat(respostas).allSatisfy(resposta -> {
			assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/grupo-reservado-ao-proprietario");
		});
	}

	// -------------------------------------------------------------- critério 4

	@Test
	void quemGerenciaUsuariosNaoTrocaOProprioGrupo() {
		UUID gestores = api.grupoPersonalizado(proprietario, "Gestores", "USUARIO_VISUALIZAR", "USUARIO_GERENCIAR");
		String email = emailUnico();
		UUID gestor = UUID.fromString(campo(api.cadastrarUsuario(proprietario, email, gestores), "$.id"));

		MvcTestResult resposta = api.trocarGrupo(api.token(email), gestor, api.grupo(proprietario, "Gerente"));

		assertThat(resposta).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/auto-troca-de-grupo");
	}

	@Test
	void semUsuarioGerenciarResponde403() {
		String email = emailUnico();
		UUID membro = api.membro(proprietario, email, "Advogado");

		MvcTestResult resposta = api.trocarGrupo(api.token(email), membro, api.grupo(proprietario, "Gerente"));

		assertThat(resposta).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/sem-permissao");
	}

	@Test
	void semTokenResponde401() {
		assertThat(mvc.put().uri("/api/usuarios/" + UUID.randomUUID() + "/grupo").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
