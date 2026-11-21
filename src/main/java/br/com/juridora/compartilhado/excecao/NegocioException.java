package br.com.juridora.compartilhado.excecao;

/**
 * Base das exceções de negócio, traduzidas para Problem Details (RFC 9457) pelo
 * {@code TratadorErros}.
 * <p>
 * {@link #tipo()} identifica o problema em {@code type} ({@code /problemas/<tipo>}).
 * A mensagem é exibida ao usuário: nunca inclua dados sensíveis.
 */
public abstract class NegocioException extends RuntimeException {

	private final String tipo;

	protected NegocioException(String tipo, String mensagem) {
		super(mensagem);
		this.tipo = tipo;
	}

	/** Identificador do problema, em minúsculas e com hífen (ex.: {@code email-ja-cadastrado}). */
	public String tipo() {
		return tipo;
	}

}
