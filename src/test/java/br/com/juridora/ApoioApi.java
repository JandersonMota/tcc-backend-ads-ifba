package br.com.juridora;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Chamadas de API repetidas nos testes de integração: cadastro de escritório (HU05),
 * login (HU06), logout (HU07) e leitura do corpo da resposta.
 */
public final class ApoioApi {

	public static final String SENHA = "senha-forte-123";

	private final MockMvcTester mvc;

	public ApoioApi(MockMvcTester mvc) {
		this.mvc = mvc;
	}

	/** Escritório cadastrado e o seu Proprietário. */
	public record Cadastro(UUID escritorioId, UUID usuarioId, String email) {
	}

	public Cadastro cadastrarEscritorio() {
		String email = emailUnico();
		MvcTestResult resposta = postar("/api/autenticacao/cadastro", """
				{"nomeEscritorio": "Escritório %s", "nome": "Proprietário", "email": "%s", "senha": "%s"}"""
				.formatted(email, email, SENHA));
		assertThat(resposta).hasStatus(HttpStatus.CREATED);
		return new Cadastro(UUID.fromString(campo(resposta, "$.escritorioId")),
				UUID.fromString(campo(resposta, "$.usuarioId")), email);
	}

	public MvcTestResult login(String email, String senha) {
		return postar("/api/autenticacao/login", """
				{"email": "%s", "senha": "%s"}""".formatted(email, senha));
	}

	/** Token de um login que precisa dar certo. */
	public String token(String email) {
		MvcTestResult resposta = login(email, SENHA);
		assertThat(resposta).hasStatus(HttpStatus.OK);
		return campo(resposta, "$.token");
	}

	public MvcTestResult perfil(String token) {
		return mvc.get().uri("/api/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	public MvcTestResult sair(String token) {
		return mvc.post().uri("/api/autenticacao/sair").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	public MvcTestResult postar(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	public static String campo(MvcTestResult resposta, String caminho) {
		Object valor = JsonPath.read(corpo(resposta), caminho);
		return String.valueOf(valor);
	}

	public static String corpo(MvcTestResult resposta) {
		try {
			return resposta.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (UnsupportedEncodingException excecao) {
			throw new IllegalStateException(excecao);
		}
	}

	public static String emailUnico() {
		return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
	}

}
