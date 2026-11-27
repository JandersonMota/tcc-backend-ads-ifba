package br.com.juridora.identidade.dominio;

import br.com.juridora.compartilhado.excecao.RecursoNaoEncontradoException;

/** Usuário inexistente ou de outro escritório (RN02: as duas situações respondem 404). */
public class UsuarioNaoEncontradoException extends RecursoNaoEncontradoException {

	public UsuarioNaoEncontradoException() {
		super("usuario-nao-encontrado", "Usuário não encontrado.");
	}

}
