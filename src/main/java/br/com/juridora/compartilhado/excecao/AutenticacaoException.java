package br.com.juridora.compartilhado.excecao;

/**
 * Credencial recusada (HTTP 401). A mensagem deve ser genérica: não indica qual dado
 * está errado nem se o usuário existe.
 */
public abstract class AutenticacaoException extends NegocioException {

	protected AutenticacaoException(String tipo, String mensagem) {
		super(tipo, mensagem);
	}

}
