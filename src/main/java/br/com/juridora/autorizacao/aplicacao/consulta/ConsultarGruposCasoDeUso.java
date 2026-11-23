package br.com.juridora.autorizacao.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grupos do escritório da execução (HU08): a listagem mostra ao proprietário quais
 * grupos pode atribuir a um usuário novo, e {@link #existe} valida o grupo escolhido.
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

	@Transactional(readOnly = true)
	public boolean existe(UUID grupoId) {
		// Falha cedo (TenantNaoDefinidoException): sem tenant o RLS diria "não existe", escondendo o erro.
		tenantContext.tenantAtual();
		return grupoId != null && grupos.existe(grupoId);
	}

}
