package br.com.juridora.autorizacao.dominio.modelo;

import br.com.juridora.compartilhado.excecao.ConflitoException;

/** Já existe grupo com esse nome no escritório, sem diferenciar maiúsculas (409). */
public class NomeDeGrupoJaUsadoException extends ConflitoException {

	public NomeDeGrupoJaUsadoException() {
		super("grupo-ja-existe", "Já existe um grupo com este nome.");
	}

}
