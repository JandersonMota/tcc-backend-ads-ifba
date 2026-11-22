package br.com.juridora.autorizacao.dominio.repositorio;

import java.util.Optional;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;

/** Porta de saída de persistência de grupos (sempre no tenant da execução). */
public interface GrupoRepositorio {

	/** Grava um grupo novo e o devolve com o ID preenchido. */
	Grupo adicionar(Grupo grupo);

	/** Grupo do escritório da execução; vazio se não existir ou for de outro escritório. */
	Optional<Grupo> buscar(UUID id);

}
