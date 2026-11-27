package br.com.juridora.autorizacao.aplicacao.casodeuso;

import java.time.Clock;
import java.util.UUID;

import br.com.juridora.autorizacao.GrupoRenomeadoEvent;
import br.com.juridora.autorizacao.aplicacao.consulta.DetalheGrupo;
import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.GrupoNaoEncontradoException;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renomeia um grupo padrão ou personalizado (HU09); o Proprietário mantém o nome. */
@Service
public class RenomearGrupoCasoDeUso {

	private final GrupoRepositorio grupos;

	private final TenantContext tenantContext;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	RenomearGrupoCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext, ApplicationEventPublisher eventos,
			Clock relogio) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/**
	 * Renomear para o mesmo nome não é erro e não gera evento.
	 *
	 * @throws GrupoNaoEncontradoException se o grupo não existir ou for de outro escritório
	 * @throws br.com.juridora.autorizacao.dominio.modelo.GrupoProprietarioProtegidoException se for o grupo Proprietário
	 * @throws br.com.juridora.autorizacao.dominio.modelo.NomeDeGrupoJaUsadoException se o nome já existir
	 */
	@Transactional
	public DetalheGrupo renomear(UUID grupoId, String novoNome) {
		Grupo grupo = grupos.buscarParaAlterar(grupoId).orElseThrow(GrupoNaoEncontradoException::new);
		String nomeAnterior = grupo.getNome();
		grupo.renomear(novoNome);
		if (grupo.getNome().equals(nomeAnterior)) {
			return DetalheGrupo.de(grupo);
		}
		Grupo atualizado = grupos.atualizar(grupo);
		eventos.publishEvent(new GrupoRenomeadoEvent(tenantContext.tenantAtual(), grupoId, nomeAnterior,
				atualizado.getNome(), tenantContext.usuarioAtual(), relogio.instant()));
		return DetalheGrupo.de(atualizado);
	}

}
