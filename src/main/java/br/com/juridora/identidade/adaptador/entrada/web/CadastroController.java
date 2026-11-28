package br.com.juridora.identidade.adaptador.entrada.web;

import java.net.URI;

import br.com.juridora.identidade.aplicacao.CadastroEscritorioServico;
import br.com.juridora.identidade.aplicacao.ResultadoCadastro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro público de escritório (HU05). Rota liberada ({@code permitAll}) na
 * configuração de segurança (HU06).
 */
@RestController
@RequestMapping("/api/autenticacao")
@Tag(name = "Autenticação", description = "Cadastro de escritório e login (públicos) e logout")
class CadastroController {

	static final URI LOCAL_ESCRITORIO = URI.create("/api/escritorio");

	private final CadastroEscritorioServico cadastro;

	CadastroController(CadastroEscritorioServico cadastro) {
		this.cadastro = cadastro;
	}

	@PostMapping("/cadastro")
	@PreAuthorize("permitAll()")
	@SecurityRequirements
	ResponseEntity<CadastroResponse> cadastrar(@Valid @RequestBody CadastrarEscritorioRequest request) {
		ResultadoCadastro resultado = cadastro.cadastrar(request.paraComando());
		return ResponseEntity.created(LOCAL_ESCRITORIO)
				.body(new CadastroResponse(resultado.escritorioId(), resultado.usuarioId()));
	}

}
