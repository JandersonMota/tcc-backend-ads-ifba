package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.UUID;

import br.com.juridora.identidade.aplicacao.EncerrarSessaoServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout (HU07). Rota autenticada: encerra o token enviado no cabeçalho
 * {@code Authorization}, que passa a responder 401 em qualquer rota.
 */
@RestController
@RequestMapping("/api/autenticacao")
@Tag(name = "Autenticação", description = "Cadastro de escritório e login (públicos) e logout")
class SairController {

	private final EncerrarSessaoServico encerrarSessao;

	SairController(EncerrarSessaoServico encerrarSessao) {
		this.encerrarSessao = encerrarSessao;
	}

	@PostMapping("/sair")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Encerra a sessão", description = "Revoga o token da requisição até ele expirar.")
	void sair(@AuthenticationPrincipal Jwt token) {
		// O filtro de segurança já recusou tokens sem jti válido.
		encerrarSessao.encerrar(UUID.fromString(token.getId()), token.getExpiresAt());
	}

}
