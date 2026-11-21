package br.com.juridora.identidade.adaptador.entrada.web;

import java.net.URI;

import br.com.juridora.identidade.aplicacao.CadastroEscritorioServico;
import br.com.juridora.identidade.aplicacao.ResultadoCadastro;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro público de escritório (HU05). Quando o Spring Security entrar (HU06), esta
 * rota deve ficar liberada ({@code permitAll}).
 */
@RestController
@RequestMapping("/api/autenticacao")
class CadastroController {

	static final URI LOCAL_ESCRITORIO = URI.create("/api/escritorio");

	private final CadastroEscritorioServico cadastro;

	CadastroController(CadastroEscritorioServico cadastro) {
		this.cadastro = cadastro;
	}

	@PostMapping("/cadastro")
	ResponseEntity<CadastroResponse> cadastrar(@Valid @RequestBody CadastrarEscritorioRequest request) {
		ResultadoCadastro resultado = cadastro.cadastrar(request.paraComando());
		return ResponseEntity.created(LOCAL_ESCRITORIO)
				.body(new CadastroResponse(resultado.escritorioId(), resultado.usuarioId()));
	}

}
