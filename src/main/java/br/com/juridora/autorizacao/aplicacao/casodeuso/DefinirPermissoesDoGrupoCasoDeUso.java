package br.com.juridora.autorizacao.aplicacao.casodeuso;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import br.com.juridora.autorizacao.PermissoesDoGrupoAlteradasEvent;
import br.com.juridora.autorizacao.aplicacao.consulta.DetalheGrupo;
import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.GrupoNaoEncontradoException;
import br.com.juridora.autorizacao.dominio.modelo.Permissao;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Substitui as permissões de um grupo (HU09, RF06), padrão ou personalizado, exceto o
 * Proprietário (RN04). Vale para os usuários do grupo no próximo login, em até 15 minutos
 * (as permissões ficam no token, ADR-014).
 */
@Service
public class DefinirPermissoesDoGrupoCasoDeUso {

	private final GrupoRepositorio grupos;

	private final TenantContext tenantContext;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	DefinirPermissoesDoGrupoCasoDeUso(GrupoRepositorio grupos, TenantContext tenantContext,
			ApplicationEventPublisher eventos, Clock relogio) {
		this.grupos = grupos;
		this.tenantContext = tenantContext;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/**
	 * Gravar o mesmo conjunto não é erro e não gera evento.
	 *
	 * @throws br.com.juridora.compartilhado.excecao.ValidacaoException se uma permissão não existir
	 * @throws GrupoNaoEncontradoException se o grupo não existir ou for de outro escritório
	 * @throws br.com.juridora.autorizacao.dominio.modelo.GrupoProprietarioProtegidoException se for o grupo Proprietário
	 */
	@Transactional
	public DetalheGrupo definir(UUID grupoId, Collection<String> codigos) {
		Set<Permissao> novas = Permissao.dosCodigos(codigos);
		Grupo grupo = grupos.buscarParaAlterar(grupoId).orElseThrow(GrupoNaoEncontradoException::new);
		Set<Permissao> anteriores = grupo.getPermissoes();
		grupo.definirPermissoes(novas);
		if (anteriores.equals(novas)) {
			return DetalheGrupo.de(grupo);
		}
		Grupo atualizado = grupos.atualizar(grupo);
		eventos.publishEvent(new PermissoesDoGrupoAlteradasEvent(tenantContext.tenantAtual(), grupoId,
				Set.copyOf(Permissao.codigos(anteriores)), Set.copyOf(Permissao.codigos(novas)),
				tenantContext.usuarioAtual(), relogio.instant()));
		return DetalheGrupo.de(atualizado);
	}

}
