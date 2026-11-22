package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respostas 401 e 403 do Spring Security em Problem Details (RFC 9457), no mesmo formato
 * do {@code TratadorErros}. O corpo é fixo: não diz por que o token foi recusado
 * (ausente, expirado, assinatura inválida), para não ajudar quem tenta forjar um.
 */
class RespostaErroSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final String NAO_AUTENTICADO = """
			{"type":"/problemas/nao-autenticado","title":"Não autenticado","status":401,\
			"detail":"Autenticação necessária: envie um token válido no cabeçalho Authorization."}""";

	private static final String SEM_PERMISSAO = """
			{"type":"/problemas/sem-permissao","title":"Sem permissão","status":403,\
			"detail":"Você não tem permissão para esta operação."}""";

	@Override
	public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
			AuthenticationException excecao) throws IOException {
		resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		escrever(resposta, HttpStatus.UNAUTHORIZED, NAO_AUTENTICADO);
	}

	@Override
	public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
			AccessDeniedException excecao) throws IOException {
		escrever(resposta, HttpStatus.FORBIDDEN, SEM_PERMISSAO);
	}

	private static void escrever(HttpServletResponse resposta, HttpStatus status, String corpo) throws IOException {
		resposta.setStatus(status.value());
		resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resposta.getWriter().write(corpo);
	}

}
