package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.LoginServico;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login com e-mail e senha (HU06). Rota pública ({@code permitAll} na configuração de segurança). */
@RestController
@RequestMapping("/api/autenticacao")
@Tag(name = "Autenticação", description = "Cadastro de escritório e login (públicos) e logout")
class LoginController {

	private final LoginServico login;

	LoginController(LoginServico login) {
		this.login = login;
	}

	@PostMapping("/login")
	@PreAuthorize("permitAll()")
	@SecurityRequirements
	LoginResponse entrar(@Valid @RequestBody LoginRequest request) {
		return LoginResponse.de(login.entrar(request.paraComando()));
	}

}
