package br.com.juridora.autorizacao.aplicacao.casodeuso;

import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.GrupoPadrao;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria os cinco grupos padrão no escritório da execução (HU05, RF05). Exposto por {@code AutorizacaoFachada}. */
@Service
public class CriarGruposPadraoCasoDeUso {

	private final GrupoRepositorio grupos;
	private final TenantContext tenantContext;

	CriarGruposPadraoCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
	}

	@Transactional
	public UUID criarGruposPadrao() {
		// Falha cedo (TenantNaoDefinidoException) em vez de depender só do RLS.
		tenantContext.tenantAtual();
		UUID proprietario = null;
		for (GrupoPadrao definicao : GrupoPadrao.values()) {
			Grupo grupo = grupos.adicionar(Grupo.padrao(definicao));
			if (definicao == GrupoPadrao.PROPRIETARIO) {
				proprietario = grupo.getId();
			}
		}
		return proprietario;
	}

}
