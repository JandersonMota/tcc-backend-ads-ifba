package br.com.juridora.autorizacao.aplicacao.casodeuso;

import java.time.Clock;
import java.util.Set;

import br.com.juridora.autorizacao.GrupoCriadoEvent;
import br.com.juridora.autorizacao.aplicacao.comando.CriarGrupoComando;
import br.com.juridora.autorizacao.aplicacao.consulta.DetalheGrupo;
import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.Permissao;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria um grupo personalizado no escritório da execução (HU09, RF05). */
@Service
public class CriarGrupoCasoDeUso {

	private final GrupoRepositorio grupos;

	private final TenantContext tenantContext;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	CriarGrupoCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext, ApplicationEventPublisher eventos,
			Clock relogio) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/**
	 * @throws br.com.juridora.compartilhado.excecao.ValidacaoException se o nome ou uma permissão forem inválidos
	 * @throws br.com.juridora.autorizacao.dominio.modelo.NomeDeGrupoJaUsadoException se o nome já existir
	 */
	@Transactional
	public DetalheGrupo criar(CriarGrupoComando comando) {
		Set<Permissao> permissoes = Permissao.dosCodigos(comando.permissoes());
		Grupo grupo = grupos.adicionar(Grupo.personalizado(comando.nome(), permissoes));
		eventos.publishEvent(new GrupoCriadoEvent(tenantContext.tenantAtual(), grupo.getId(), grupo.getNome(),
				Set.copyOf(Permissao.codigos(permissoes)), tenantContext.usuarioAtual(), relogio.instant()));
		return DetalheGrupo.de(grupo);
	}

}
