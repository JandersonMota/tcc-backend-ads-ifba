package br.com.juridora.identidade.dominio;

import br.com.juridora.compartilhado.excecao.ConflitoException;

/** O e-mail de login já pertence a um usuário da plataforma (HU05, critério 2). */
public class EmailJaCadastradoException extends ConflitoException {

	public EmailJaCadastradoException() {
		super("email-ja-cadastrado", "Este e-mail já está cadastrado.");
	}

}
