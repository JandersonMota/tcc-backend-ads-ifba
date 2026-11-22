package br.com.juridora.identidade.aplicacao;

import br.com.juridora.compartilhado.excecao.AutenticacaoException;

/**
 * Login recusado (HU06, critério 2). A mesma mensagem para senha errada, e-mail
 * inexistente e usuário desativado: não indica qual dado está errado.
 */
public class CredenciaisInvalidasException extends AutenticacaoException {

	public CredenciaisInvalidasException() {
		super("credenciais-invalidas", "E-mail ou senha inválidos.");
	}

}
