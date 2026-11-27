package br.com.juridora.autorizacao.dominio.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;

/** Porta de saída de persistência de grupos (sempre no tenant da execução). */
public interface GrupoRepositorio {

	/**
	 * Grava um grupo novo e o devolve com o ID preenchido.
	 *
	 * @throws br.com.juridora.autorizacao.dominio.modelo.NomeDeGrupoJaUsadoException se o nome já existir
	 */
	Grupo adicionar(Grupo grupo);

	/**
	 * Grava as alterações de um grupo já existente.
	 *
	 * @throws br.com.juridora.autorizacao.dominio.modelo.NomeDeGrupoJaUsadoException se o nome já existir
	 */
	Grupo atualizar(Grupo grupo);

	void remover(Grupo grupo);

	/** Grupo do escritório da execução; vazio se não existir ou for de outro escritório. */
	Optional<Grupo> buscar(UUID id);

	/**
	 * Como {@link #buscar}, travando o grupo para alteração até o fim da transação
	 * ({@code FOR UPDATE}): quem altera ou exclui o mesmo grupo espera.
	 */
	Optional<Grupo> buscarParaAlterar(UUID id);

	/**
	 * {@code true} se o grupo existe no escritório da execução. Trava o grupo para leitura
	 * ({@code FOR SHARE}) até o fim da transação de quem chama, para que ele não seja
	 * excluído enquanto um usuário é colocado nele.
	 */
	boolean existe(UUID id);

	/** Grupos do escritório da execução, em ordem de nome. */
	List<Grupo> listar();

}
