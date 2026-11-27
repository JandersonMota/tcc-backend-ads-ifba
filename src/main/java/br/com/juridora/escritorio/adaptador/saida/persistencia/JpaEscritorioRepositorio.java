package br.com.juridora.escritorio.adaptador.saida.persistencia;

import java.util.Optional;
import java.util.UUID;

import br.com.juridora.escritorio.dominio.modelo.Escritorio;
import br.com.juridora.escritorio.dominio.repositorio.EscritorioRepositorio;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * Implementação JPA da porta {@link EscritorioRepositorio}.
 * <p>
 * Usa {@code persist} (e não {@code save}/{@code merge}): o ID vem preenchido e o
 * escritório é sempre novo, então não há {@code SELECT} prévio.
 */
@Repository
class JpaEscritorioRepositorio implements EscritorioRepositorio {

	private final EntityManager entityManager;

	JpaEscritorioRepositorio(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public void adicionar(Escritorio escritorio) {
		entityManager.persist(escritorio);
	}

	@Override
	public Optional<Escritorio> buscar(UUID id) {
		return Optional.ofNullable(entityManager.find(Escritorio.class, id));
	}

}
