package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.List;

import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarGruposCasoDeUso;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de permissões que podem ser dadas a um grupo (HU09); só o proprietário edita grupos. */
@RestController
@RequestMapping("/api/permissoes")
@Tag(name = "Grupos", description = "Grupos de permissões do escritório")
class PermissaoController {

	private final ConsultarGruposCasoDeUso consultarGrupos;

	PermissaoController(ConsultarGruposCasoDeUso consultarGrupos) {
		this.consultarGrupos = consultarGrupos;
	}

	@GetMapping
	@PreAuthorize("hasRole('PROPRIETARIO')")
	List<String> catalogo() {
		return consultarGrupos.catalogo();
	}

}
