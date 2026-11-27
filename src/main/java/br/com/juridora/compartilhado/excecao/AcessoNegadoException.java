package br.com.juridora.compartilhado.excecao;

/**
 * Operação negada por regra que depende do recurso, e não só da permissão do token
 * (HTTP 403, mesmo {@code type} do {@code @PreAuthorize}). Ex.: só o proprietário altera
 * o próprio cadastro.
 */
public class AcessoNegadoException extends NegocioException {

	public AcessoNegadoException(String mensagem) {
		super("sem-permissao", mensagem);
	}

}
