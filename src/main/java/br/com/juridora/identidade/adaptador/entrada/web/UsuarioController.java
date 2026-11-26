package br.com.juridora.identidade.adaptador.entrada.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import br.com.juridora.identidade.aplicacao.DadosUsuario;
import br.com.juridora.identidade.aplicacao.GestaoUsuariosServico;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usuários do escritório (HU08, HU10). Ler exige {@code USUARIO_VISUALIZAR}; cadastrar, editar,
 * trocar o grupo, desativar e reativar exigem {@code USUARIO_GERENCIAR} (Matriz de Permissões). O
 * escritório vem do token; usuário de outro escritório responde 404.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Equipe do escritório: cadastro, edição, desativação e reativação")
class UsuarioController {

	/** Campos aceitos em {@code sort}; os demais são ignorados (não expõe a ordenação por senha, por exemplo). */
	private static final Set<String> ORDENACOES = Set.of("nome", "email", "ativo");

	private final GestaoUsuariosServico gestao;

	UsuarioController(GestaoUsuariosServico gestao) {
		this.gestao = gestao;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USUARIO_VISUALIZAR')")
	PagedModel<UsuarioResponse> listar(@PageableDefault(size = 20, sort = "nome") Pageable pagina) {
		return new PagedModel<>(gestao.listar(comOrdenacaoPermitida(pagina)).map(UsuarioResponse::de));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('USUARIO_VISUALIZAR')")
	UsuarioResponse buscar(@PathVariable UUID id) {
		return UsuarioResponse.de(gestao.buscar(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastrarUsuarioRequest request) {
		DadosUsuario novo = gestao.cadastrar(request.paraComando());
		return ResponseEntity.created(URI.create("/api/usuarios/" + novo.id())).body(UsuarioResponse.de(novo));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	UsuarioResponse alterar(@PathVariable UUID id, @Valid @RequestBody AlterarUsuarioRequest request) {
		return UsuarioResponse.de(gestao.alterar(id, request.paraComando()));
	}

	@PutMapping("/{id}/grupo")
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	UsuarioResponse trocarGrupo(@PathVariable UUID id, @Valid @RequestBody TrocarGrupoRequest request) {
		return UsuarioResponse.de(gestao.trocarGrupo(id, request.grupoId()));
	}

	@PostMapping("/{id}/desativar")
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@PathVariable UUID id) {
		gestao.desativar(id);
	}

	@PostMapping("/{id}/reativar")
	@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@PathVariable UUID id) {
		gestao.reativar(id);
	}

	private static Pageable comOrdenacaoPermitida(Pageable pagina) {
		Sort ordenacao = Sort.by(pagina.getSort().filter(ordem -> ORDENACOES.contains(ordem.getProperty())).toList());
		if (ordenacao.isUnsorted()) {
			ordenacao = Sort.by("nome");
		}
		return PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), ordenacao);
	}

}
