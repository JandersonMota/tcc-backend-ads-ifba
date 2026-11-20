package br.com.juridora.compartilhado.excecao;

/** Operação em conflito com um dado já existente, como uma chave única (HTTP 409). */
public abstract class ConflitoException extends NegocioException {

	protected ConflitoException(String tipo, String mensagem) {
		super(tipo, mensagem);
	}

}
