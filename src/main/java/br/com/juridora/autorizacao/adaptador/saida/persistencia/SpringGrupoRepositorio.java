package br.com.juridora.autorizacao.adaptador.saida.persistencia;

import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data de {@link Grupo}; filtrado pelo tenant da sessão ({@code @TenantId}). */
interface SpringGrupoRepositorio extends JpaRepository<Grupo, UUID> {

	List<Grupo> findAllByOrderByNomeAsc();

}
