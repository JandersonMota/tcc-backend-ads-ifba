package br.com.juridora.compartilhado.excecao;

/** Dado inválido rejeitado pelo domínio (HTTP 400). */
public class ValidacaoException extends NegocioException {

	public ValidacaoException(String mensagem) {
		super("dados-invalidos", mensagem);
	}

}
