package br.com.juridora.compartilhado.excecao;

/**
 * Regra de negócio violada com dados válidos, como o limite de usuários do plano
 * (HTTP 422).
 */
public abstract class RegraNegocioException extends NegocioException {

	protected RegraNegocioException(String tipo, String mensagem) {
		super(tipo, mensagem);
	}

}
