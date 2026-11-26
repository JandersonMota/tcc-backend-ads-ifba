package br.com.juridora.autorizacao.adaptador.saida.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Spring Data de {@link Grupo}; filtrado pelo tenant da sessão ({@code @TenantId}). */
interface SpringGrupoRepositorio extends JpaRepository<Grupo, UUID> {

	List<Grupo> findAllByOrderByNomeAsc();

	/** {@code SELECT ... FOR UPDATE}. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Grupo g where g.id = :id")
	Optional<Grupo> buscarComTravaDeEscrita(UUID id);

	/** {@code SELECT ... FOR SHARE}: impede a exclusão, mas não bloqueia outras leituras. */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select g.id from Grupo g where g.id = :id")
	Optional<UUID> buscarComTravaDeLeitura(UUID id);

}
