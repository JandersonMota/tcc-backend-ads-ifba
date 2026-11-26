package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.aplicacao.casodeuso.CriarGrupoCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.casodeuso.DefinirPermissoesDoGrupoCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.casodeuso.ExcluirGrupoCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.casodeuso.RenomearGrupoCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarGruposCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.consulta.DetalheGrupo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grupos do escritório. A listagem exige {@code USUARIO_GERENCIAR}, porque serve para
 * escolher o grupo de um usuário (HU08). Consultar, criar, renomear, editar permissões e
 * excluir grupos são operações exclusivas do proprietário (HU09, RN04): nenhuma permissão
 * atribuível dá esse acesso (RN05). Grupo de outro escritório responde 404.
 */
@RestController
@RequestMapping("/api/grupos")
@Tag(name = "Grupos", description = "Grupos de permissões do escritório")
class GrupoController {

	private final ConsultarGruposCasoDeUso consultarGrupos;

	private final CriarGrupoCasoDeUso criarGrupo;

	private final RenomearGrupoCasoDeUso renomearGrupo;

	private final DefinirPermissoesDoGrupoCasoDeUso definirPermissoes;

	private final ExcluirGrupoCasoDeUso excluirGrupo;

	GrupoController(ConsultarGruposCasoDeUso consultarGrupos, CriarGrupoCasoDeUso criarGrupo,
			RenomearGrupoCasoDeUso renomearGrupo, DefinirPermissoesDoGrupoCasoDeUso definirPermissoes,
			ExcluirGrupoCasoDeUso excluirGrupo) {
		this.consultarGrupos = consultarGrupos;
		this.criarGrupo = criarGrupo;
		this.renomearGrupo = renomearGrupo;
		this.definirPermissoes = definirPermissoes;
		this.excluirGrupo = excluirGrupo;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	List<GrupoResponse> listar() {
		return consultarGrupos.listar().stream().map(GrupoResponse::de).toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('PROPRIETARIO')")
	DetalheGrupoResponse detalhar(@PathVariable UUID id) {
		return DetalheGrupoResponse.de(consultarGrupos.detalhar(id));
	}

	@PostMapping
	@PreAuthorize("hasRole('PROPRIETARIO')")
	ResponseEntity<DetalheGrupoResponse> criar(@Valid @RequestBody CriarGrupoRequest request) {
		DetalheGrupo novo = criarGrupo.criar(request.paraComando());
		return ResponseEntity.created(URI.create("/api/grupos/" + novo.id())).body(DetalheGrupoResponse.de(novo));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('PROPRIETARIO')")
	DetalheGrupoResponse renomear(@PathVariable UUID id, @Valid @RequestBody RenomearGrupoRequest request) {
		return DetalheGrupoResponse.de(renomearGrupo.renomear(id, request.nome()));
	}

	@PutMapping("/{id}/permissoes")
	@PreAuthorize("hasRole('PROPRIETARIO')")
	DetalheGrupoResponse definirPermissoes(@PathVariable UUID id,
			@Valid @RequestBody DefinirPermissoesRequest request) {
		return DetalheGrupoResponse.de(definirPermissoes.definir(id, request.permissoes()));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('PROPRIETARIO')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void excluir(@PathVariable UUID id) {
		excluirGrupo.excluir(id);
	}

}
