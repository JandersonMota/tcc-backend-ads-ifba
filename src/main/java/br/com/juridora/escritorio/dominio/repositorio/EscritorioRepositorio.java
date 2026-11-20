package br.com.juridora.escritorio.dominio.repositorio;

import br.com.juridora.escritorio.dominio.modelo.Escritorio;

/** Porta de saída de persistência de escritórios. */
public interface EscritorioRepositorio {

	/** Grava um escritório novo (o ID já vem preenchido). */
	void adicionar(Escritorio escritorio);

}
