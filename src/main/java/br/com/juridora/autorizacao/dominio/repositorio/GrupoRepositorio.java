package br.com.juridora.autorizacao.dominio.repositorio;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;

/** Porta de saída de persistência de grupos (sempre no tenant da execução). */
public interface GrupoRepositorio {

	/** Grava um grupo novo e o devolve com o ID preenchido. */
	Grupo adicionar(Grupo grupo);

}
