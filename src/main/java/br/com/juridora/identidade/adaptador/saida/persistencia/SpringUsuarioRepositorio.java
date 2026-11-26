package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.util.UUID;

import br.com.juridora.identidade.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data de {@link Usuario}; filtrado pelo tenant da sessão ({@code @TenantId}). */
interface SpringUsuarioRepositorio extends JpaRepository<Usuario, UUID> {

	long countByAtivoTrue();

	boolean existsByGrupoId(UUID grupoId);

}
