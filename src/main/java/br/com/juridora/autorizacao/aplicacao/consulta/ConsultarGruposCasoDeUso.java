package br.com.juridora.autorizacao.aplicacao.consulta;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.GrupoNaoEncontradoException;
import br.com.juridora.autorizacao.dominio.modelo.Permissao;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grupos do escritório da execução: a listagem mostra quais grupos podem ser atribuídos
 * a um usuário (HU08), {@link #existe} valida o grupo escolhido, e {@link #detalhar} e
 * {@link #catalogo} servem à edição de permissões (HU09).
 */
@Service
public class ConsultarGruposCasoDeUso {

	/** Grupo como aparece na listagem. */
	public record GrupoResumo(UUID id, String nome, boolean padrao) {
	}

	private final GrupoRepositorio grupos;

	private final TenantContext tenantContext;

	ConsultarGruposCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
	}

	@Transactional(readOnly = true)
	public List<GrupoResumo> listar() {
		tenantContext.tenantAtual();
		return grupos.listar().stream()
				.map(grupo -> new GrupoResumo(grupo.getId(), grupo.getNome(), grupo.isPadrao()))
				.toList();
	}

	/** @throws GrupoNaoEncontradoException se o grupo não existir ou for de outro escritório */
	@Transactional(readOnly = true)
	public DetalheGrupo detalhar(UUID grupoId) {
		tenantContext.tenantAtual();
		return grupos.buscar(grupoId).map(DetalheGrupo::de).orElseThrow(GrupoNaoEncontradoException::new);
	}

	/** Catálogo de permissões atribuíveis (Matriz de Permissões, §2), na ordem do catálogo. */
	public List<String> catalogo() {
		return Arrays.stream(Permissao.values()).map(Permissao::name).toList();
	}

	/**
	 * Não é {@code readOnly}: a verificação trava o grupo ({@code FOR SHARE}) até o fim da
	 * transação de quem chama, para que ele não seja excluído enquanto recebe um usuário.
	 */
	@Transactional
	public boolean existe(UUID grupoId) {
		// Falha cedo (TenantNaoDefinidoException): sem tenant o RLS diria "não existe", escondendo o erro.
		tenantContext.tenantAtual();
		return grupoId != null && grupos.existe(grupoId);
	}

}
