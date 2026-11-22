package br.com.juridora.autorizacao.aplicacao.consulta;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.juridora.autorizacao.dominio.modelo.Permissao;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permissões de um grupo do escritório da execução (HU06: viram a claim {@code permissoes}). */
@Service
public class ConsultarPermissoesDoGrupoCasoDeUso {

	private final GrupoRepositorio grupos;

	private final TenantContext tenantContext;

	ConsultarPermissoesDoGrupoCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
	}

	@Transactional(readOnly = true)
	public Set<String> permissoesDoGrupo(UUID grupoId) {
		// Falha cedo (TenantNaoDefinidoException): sem tenant o RLS devolveria vazio, escondendo o erro.
		tenantContext.tenantAtual();
		return grupos.buscar(grupoId)
				.map(grupo -> grupo.getPermissoes().stream().map(Permissao::name).collect(Collectors.toUnmodifiableSet()))
				.orElseGet(Set::of);
	}

}
