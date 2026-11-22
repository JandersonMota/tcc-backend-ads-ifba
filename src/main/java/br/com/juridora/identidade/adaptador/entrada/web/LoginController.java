package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.LoginServico;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login com e-mail e senha (HU06). Rota pública ({@code permitAll} na configuração de segurança). */
@RestController
@RequestMapping("/api/autenticacao")
@Tag(name = "Autenticação", description = "Cadastro de escritório e login (rotas públicas)")
class LoginController {

	private final LoginServico login;

	LoginController(LoginServico login) {
		this.login = login;
	}

	@PostMapping("/login")
	@SecurityRequirements
	LoginResponse entrar(@Valid @RequestBody LoginRequest request) {
		return LoginResponse.de(login.entrar(request.paraComando()));
	}

}
