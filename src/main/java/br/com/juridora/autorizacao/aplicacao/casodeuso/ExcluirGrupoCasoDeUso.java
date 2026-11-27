package br.com.juridora.autorizacao.aplicacao.casodeuso;

import java.time.Clock;
import java.util.UUID;

import br.com.juridora.autorizacao.GrupoExcluidoEvent;
import br.com.juridora.autorizacao.MembrosDoGrupo;
import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.GrupoComUsuariosException;
import br.com.juridora.autorizacao.dominio.modelo.GrupoNaoEncontradoException;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclui um grupo padrão ou personalizado sem usuários (HU09, RN07); o Proprietário nunca sai.
 * <p>
 * Concorrência: o grupo é travado ({@code FOR UPDATE}) antes de contar os usuários. Quem
 * coloca um usuário no grupo trava o mesmo registro para leitura
 * ({@link br.com.juridora.autorizacao.AutorizacaoApi#grupoExiste}), então as duas operações
 * não se cruzam: ou o usuário entra antes e a exclusão é recusada, ou o grupo some antes e
 * o cadastro do usuário é recusado.
 */
@Service
public class ExcluirGrupoCasoDeUso {

	private final GrupoRepositorio grupos;

	private final MembrosDoGrupo membros;

	private final TenantContext tenantContext;

	private final ApplicationEventPublisher eventos;

	private final Clock relogio;

	ExcluirGrupoCasoDeUso(GrupoRepositorio grupos, MembrosDoGrupo membros, TenantContext tenantContext,
			ApplicationEventPublisher eventos, Clock relogio) {
		this.grupos = grupos;
		this.membros = membros;
		this.tenantContext = tenantContext;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/**
	 * @throws GrupoNaoEncontradoException se o grupo não existir ou for de outro escritório
	 * @throws br.com.juridora.autorizacao.dominio.modelo.GrupoProprietarioProtegidoException se for o grupo Proprietário
	 * @throws GrupoComUsuariosException se algum usuário, ativo ou não, estiver no grupo
	 */
	@Transactional
	public void excluir(UUID grupoId) {
		Grupo grupo = grupos.buscarParaAlterar(grupoId).orElseThrow(GrupoNaoEncontradoException::new);
		grupo.garantirExcluivel();
		if (membros.possuiMembros(grupoId)) {
			throw new GrupoComUsuariosException();
		}
		grupos.remover(grupo);
		eventos.publishEvent(new GrupoExcluidoEvent(tenantContext.tenantAtual(), grupoId, grupo.getNome(),
				tenantContext.usuarioAtual(), relogio.instant()));
	}

}
