package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.PerfilServico;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dados e permissões efetivas do usuário autenticado. Não recebe nenhum identificador:
 * usuário e escritório vêm do token.
 */
@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil", description = "Dados e permissões do usuário autenticado")
class PerfilController {

	private final PerfilServico perfil;

	PerfilController(PerfilServico perfil) {
		this.perfil = perfil;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	PerfilResponse consultar() {
		return PerfilResponse.de(perfil.perfilAtual());
	}

}
