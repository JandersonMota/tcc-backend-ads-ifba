package br.com.juridora.compartilhado.excecao;

/**
 * Recurso inexistente <strong>ou de outro escritório</strong> (HTTP 404, RN02): as duas
 * situações recebem a mesma resposta, para não revelar o que existe em outro tenant.
 */
public abstract class RecursoNaoEncontradoException extends NegocioException {

	protected RecursoNaoEncontradoException(String tipo, String mensagem) {
		super(tipo, mensagem);
	}

}
