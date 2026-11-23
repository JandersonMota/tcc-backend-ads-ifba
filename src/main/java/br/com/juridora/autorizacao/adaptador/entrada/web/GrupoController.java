package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.List;

import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarGruposCasoDeUso;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grupos do escritório. Na HU08 só a listagem, para o proprietário escolher o grupo do
 * usuário que está cadastrando; criar e editar grupos é a HU09.
 */
@RestController
@RequestMapping("/api/grupos")
@Tag(name = "Grupos", description = "Grupos de permissões do escritório")
class GrupoController {

	private final ConsultarGruposCasoDeUso consultarGrupos;

	GrupoController(ConsultarGruposCasoDeUso consultarGrupos) {
		this.consultarGrupos = consultarGrupos;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	List<GrupoResponse> listar() {
		return consultarGrupos.listar().stream().map(GrupoResponse::de).toList();
	}

}
