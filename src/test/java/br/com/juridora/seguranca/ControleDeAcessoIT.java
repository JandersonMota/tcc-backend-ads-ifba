package br.com.juridora.seguranca;

import static br.com.juridora.ApoioApi.campo;
import static br.com.juridora.ApoioApi.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HU11 – toda ação verifica as permissões do usuário, em todas as rotas protegidas da API,
 * com PostgreSQL real e RLS valendo. Complementa o {@code ControleDeAcessoTest}, que garante
 * que todo endpoint declara uma regra; aqui se confere que a regra é aplicada.
 * <p>
 * Os corpos enviados são válidos: a validação do corpo (400) acontece antes do
 * {@code @PreAuthorize} do controller, e o que se quer medir aqui é a permissão.
 * <p>
 * Cada teste precisa de vários membros; o limite de usuários ativos do plano (RN09, testado
 * no {@code GestaoUsuariosIT}) é elevado só aqui.
 */
@SpringBootTest(properties = "juridora.planos.free.usuarios-ativos=10")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ControleDeAcessoIT {

	@Autowired
	MockMvcTester mvc;

	ApoioApi api;

	String proprietario;

	UUID alvo;

	UUID advogado;

	UUID personalizado;

	@BeforeEach
	void preparar() {
		api = new ApoioApi(mvc);
		Cadastro escritorio = api.cadastrarEscritorio();
		proprietario = api.token(escritorio.email());
		alvo = api.membro(proprietario, emailUnico(), "Advogado");
		advogado = api.grupo(proprietario, "Advogado");
		personalizado = api.grupoPersonalizado(proprietario, "Recepção");
	}

	/** Rota protegida, a regra que ela exige e uma chamada válida com o token informado. */
	record Rota(String descricao, String regra, Function<String, MvcTestResult> chamada) {

		MvcTestResult com(String token) {
			return chamada.apply(token);
		}

	}

	private static final String PROPRIETARIO = "hasRole('PROPRIETARIO')";

	/** Todas as rotas protegidas da API, na ordem em que podem ser chamadas com sucesso. */
	private List<Rota> rotas() {
		String usuario = "/api/usuarios/" + alvo;
		String grupo = "/api/grupos/" + personalizado;
		return List.of(
				new Rota("listar usuários", "USUARIO_VISUALIZAR", t -> api.obter(t, "/api/usuarios")),
				new Rota("buscar usuário", "USUARIO_VISUALIZAR", t -> api.obter(t, usuario)),
				new Rota("listar grupos", "USUARIO_GERENCIAR", t -> api.obter(t, "/api/grupos")),
				new Rota("cadastrar usuário", "USUARIO_GERENCIAR",
						t -> api.cadastrarUsuario(t, emailUnico(), advogado)),
				new Rota("editar usuário", "USUARIO_GERENCIAR", t -> api.alterar(t, usuario, """
						{"nome": "Alvo", "email": "%s"}""".formatted(emailUnico()))),
				new Rota("trocar grupo", "USUARIO_GERENCIAR", t -> api.trocarGrupo(t, alvo, advogado)),
				new Rota("desativar usuário", "USUARIO_GERENCIAR", t -> api.acionar(t, usuario + "/desativar")),
				new Rota("reativar usuário", "USUARIO_GERENCIAR", t -> api.acionar(t, usuario + "/reativar")),
				new Rota("catálogo de permissões", PROPRIETARIO, t -> api.obter(t, "/api/permissoes")),
				new Rota("detalhar grupo", PROPRIETARIO, t -> api.obter(t, grupo)),
				new Rota("criar grupo", PROPRIETARIO, t -> api.criarGrupo(t, "Grupo " + UUID.randomUUID())),
				new Rota("renomear grupo", PROPRIETARIO, t -> api.alterar(t, grupo, """
						{"nome": "Recepção %s"}""".formatted(UUID.randomUUID()))),
				new Rota("definir permissões", PROPRIETARIO,
						t -> api.alterar(t, grupo + "/permissoes", "{\"permissoes\": []}")),
				new Rota("excluir grupo", PROPRIETARIO, t -> api.excluir(t, grupo)));
	}

	// -------------------------------------------------------------- critério 1

	@Test
	void usuarioComAPermissaoExecutaAAcao() {
		String visualizar = membro("USUARIO_VISUALIZAR");
		String gerenciar = membro("USUARIO_GERENCIAR");

		for (Rota rota : rotas()) {
			String token = switch (rota.regra()) {
				case "USUARIO_VISUALIZAR" -> visualizar;
				case "USUARIO_GERENCIAR" -> gerenciar;
				default -> proprietario;
			};
			assertThat(rota.com(token).getResponse().getStatus()).as(rota.descricao()).isBetween(200, 299);
		}
	}

	// -------------------------------------------------------------- critério 2

	@Test
	void usuarioSemAPermissaoRecebe403EmTodaRotaProtegida() {
		String semPermissoes = membro();

		assertThat(rotas()).allSatisfy(rota -> {
			MvcTestResult resposta = rota.com(semPermissoes);
			assertThat(resposta).as(rota.descricao()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(campo(resposta, "$.type")).isEqualTo("/problemas/sem-permissao");
		});
		// Só o que é do próprio usuário dispensa permissão.
		assertThat(api.perfil(semPermissoes)).hasStatus(HttpStatus.OK);
		assertThat(api.sair(semPermissoes)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void cadaPermissaoLiberaSoAsRotasDela() {
		String visualizar = membro("USUARIO_VISUALIZAR");

		assertThat(api.obter(visualizar, "/api/usuarios")).hasStatus(HttpStatus.OK);
		assertThat(rotas().stream().filter(rota -> !rota.regra().equals("USUARIO_VISUALIZAR")))
				.allSatisfy(rota -> assertThat(rota.com(visualizar)).as(rota.descricao())
						.hasStatus(HttpStatus.FORBIDDEN));
	}

	@Test
	void semPermissaoORecursoDeOutroEscritorioTambemResponde403() {
		String semPermissoes = membro();
		String outro = api.token(api.cadastrarEscritorio().email());
		UUID usuarioDoOutro = api.membro(outro, emailUnico(), "Advogado");

		// A resposta é a mesma para recurso do outro escritório e inexistente: não revela nada.
		assertThat(api.obter(semPermissoes, "/api/usuarios/" + usuarioDoOutro)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.obter(semPermissoes, "/api/usuarios/" + UUID.randomUUID())).hasStatus(HttpStatus.FORBIDDEN);
	}

	// -------------------------------------------------------------- critério 3

	@Test
	void recursoDeOutroEscritorioResponde404MesmoComAPermissao() {
		Cadastro outroEscritorio = api.cadastrarEscritorio();
		String outro = api.token(outroEscritorio.email());
		UUID usuarioDoOutro = api.membro(outro, emailUnico(), "Advogado");
		String usuario = "/api/usuarios/" + usuarioDoOutro;
		String grupo = "/api/grupos/" + api.grupoPersonalizado(outro, "Recepção");

		List<MvcTestResult> respostas = List.of(
				api.obter(proprietario, usuario),
				api.alterar(proprietario, usuario, """
						{"nome": "Invadido", "email": "%s"}""".formatted(emailUnico())),
				api.trocarGrupo(proprietario, usuarioDoOutro, advogado),
				api.acionar(proprietario, usuario + "/desativar"),
				api.acionar(proprietario, usuario + "/reativar"),
				api.obter(proprietario, grupo),
				api.alterar(proprietario, grupo, "{\"nome\": \"Invadido\"}"),
				api.alterar(proprietario, grupo + "/permissoes", "{\"permissoes\": []}"),
				api.excluir(proprietario, grupo));

		assertThat(respostas).allSatisfy(resposta -> assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND));
		assertThat(api.obter(outro, usuario)).hasStatus(HttpStatus.OK);
		assertThat(campo(api.obter(outro, grupo), "$.nome")).isEqualTo("Recepção");
	}

	// -------------------------------------------------------------- critério 4

	@Test
	void todasAsPermissoesSemSerProprietarioNaoDaoAcessoAsOperacoesDoDono() {
		String[] catalogo = Arrays.stream(Permissao.values()).map(Permissao::name).toArray(String[]::new);
		String quaseDono = membro(catalogo);

		List<String> doPerfil = JsonPath.read(ApoioApi.corpo(api.perfil(quaseDono)), "$.permissoes");
		assertThat(doPerfil).containsExactlyInAnyOrder(catalogo);
		assertThat(rotas().stream().filter(rota -> rota.regra().equals(PROPRIETARIO)))
				.allSatisfy(rota -> assertThat(rota.com(quaseDono)).as(rota.descricao())
						.hasStatus(HttpStatus.FORBIDDEN));
	}

	@Test
	void proprietarioRecebeOCatalogoInteiro() {
		List<String> doPerfil = JsonPath.read(ApoioApi.corpo(api.perfil(proprietario)), "$.permissoes");

		assertThat(doPerfil).containsExactlyInAnyOrder(
				Arrays.stream(Permissao.values()).map(Permissao::name).toArray(String[]::new));
	}

	// ------------------------------------------------------------- operacional

	@Test
	void endpointsOperacionaisDoActuatorSaoNegadosAQualquerUsuario() {
		assertThat(api.obter(proprietario, "/actuator/modulith")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.obter(proprietario, "/actuator/info")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/actuator/info").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatus(HttpStatus.OK);
	}

	// ------------------------------------------------------------------ apoio

	/** Token de um membro novo, num grupo personalizado só com as permissões informadas. */
	private String membro(String... permissoes) {
		String nome = "Grupo " + UUID.randomUUID().toString().substring(0, 8);
		api.grupoPersonalizado(proprietario, nome, permissoes);
		String email = emailUnico();
		api.membro(proprietario, email, nome);
		return api.token(email);
	}

}
