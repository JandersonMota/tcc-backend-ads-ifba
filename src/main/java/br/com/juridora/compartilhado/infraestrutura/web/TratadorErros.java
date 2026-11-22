package br.com.juridora.compartilhado.infraestrutura.web;

import java.net.URI;
import java.util.List;

import br.com.juridora.compartilhado.excecao.AutenticacaoException;
import br.com.juridora.compartilhado.excecao.ConflitoException;
import br.com.juridora.compartilhado.excecao.LimiteExcedidoException;
import br.com.juridora.compartilhado.excecao.NegocioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz erros para Problem Details (RFC 9457), conforme a Especificação da API.
 * <ul>
 *   <li>erro de validação do corpo → 400, com a lista {@code campos};</li>
 *   <li>{@link AutenticacaoException} → 401;</li>
 *   <li>{@link ConflitoException} → 409;</li>
 *   <li>{@link LimiteExcedidoException} → 429, com {@code Retry-After} em segundos;</li>
 *   <li>demais {@link NegocioException} → 400.</li>
 * </ul>
 * As mensagens não repetem os valores enviados (ex.: senha ou e-mail).
 */
@RestControllerAdvice
class TratadorErros extends ResponseEntityExceptionHandler {

	private static final String PREFIXO_TIPO = "/problemas/";

	/** Campo inválido do corpo da requisição. */
	record CampoInvalido(String campo, String mensagem) {
	}

	@ExceptionHandler(NegocioException.class)
	ResponseEntity<ProblemDetail> tratarNegocio(NegocioException excecao) {
		HttpStatus status = switch (excecao) {
			case AutenticacaoException _ -> HttpStatus.UNAUTHORIZED;
			case ConflitoException _ -> HttpStatus.CONFLICT;
			case LimiteExcedidoException _ -> HttpStatus.TOO_MANY_REQUESTS;
			default -> HttpStatus.BAD_REQUEST;
		};
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, excecao.getMessage());
		problema.setType(URI.create(PREFIXO_TIPO + excecao.tipo()));
		problema.setTitle(titulo(status));
		ResponseEntity.BodyBuilder resposta = ResponseEntity.status(status);
		if (excecao instanceof LimiteExcedidoException limite) {
			// Arredonda para cima: Retry-After 0 faria o cliente tentar de novo cedo demais.
			long segundos = Math.max(1, (limite.aguardar().toMillis() + 999) / 1000);
			resposta.header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
		}
		return resposta.body(problema);
	}

	private static String titulo(HttpStatus status) {
		return switch (status) {
			case UNAUTHORIZED -> "Não autenticado";
			case CONFLICT -> "Conflito com dado existente";
			case TOO_MANY_REQUESTS -> "Muitas tentativas";
			default -> "Dados inválidos";
		};
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excecao,
			HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
		List<CampoInvalido> campos = excecao.getBindingResult().getFieldErrors().stream()
				.map(erro -> new CampoInvalido(erro.getField(), erro.getDefaultMessage()))
				.toList();
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Um ou mais campos estão inválidos.");
		problema.setType(URI.create(PREFIXO_TIPO + "dados-invalidos"));
		problema.setTitle("Dados inválidos");
		problema.setProperty("campos", campos);
		return handleExceptionInternal(excecao, problema, cabecalhos, HttpStatus.BAD_REQUEST, requisicao);
	}

}
