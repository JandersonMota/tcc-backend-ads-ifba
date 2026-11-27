package br.com.juridora.autorizacao.dominio.modelo;

import br.com.juridora.compartilhado.excecao.RecursoNaoEncontradoException;

/** Grupo inexistente ou de outro escritório (404, RN02). */
public class GrupoNaoEncontradoException extends RecursoNaoEncontradoException {

	public GrupoNaoEncontradoException() {
		super("grupo-nao-encontrado", "Grupo não encontrado.");
	}

}
