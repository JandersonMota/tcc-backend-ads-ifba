package br.com.juridora.identidade.aplicacao;

import br.com.juridora.compartilhado.excecao.AutenticacaoException;

/**
 * O token é válido, mas o usuário dele não existe mais ou foi desativado (RN08): a
 * requisição é tratada como não autenticada.
 */
public class UsuarioDoTokenInvalidoException extends AutenticacaoException {

	public UsuarioDoTokenInvalidoException() {
		super("nao-autenticado", "Sessão inválida. Entre novamente.");
	}

}
