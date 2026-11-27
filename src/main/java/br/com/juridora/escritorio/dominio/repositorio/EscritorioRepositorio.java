package br.com.juridora.escritorio.dominio.repositorio;

import java.util.Optional;
import java.util.UUID;

import br.com.juridora.escritorio.dominio.modelo.Escritorio;

/** Porta de saída de persistência de escritórios. */
public interface EscritorioRepositorio {

	/** Grava um escritório novo (o ID já vem preenchido). */
	void adicionar(Escritorio escritorio);

	/** Escritório pelo ID (quem chama passa o tenant da execução, nunca um ID da requisição). */
	Optional<Escritorio> buscar(UUID id);

}
