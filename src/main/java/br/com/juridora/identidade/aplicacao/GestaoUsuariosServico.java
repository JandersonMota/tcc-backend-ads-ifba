package br.com.juridora.identidade.aplicacao;

import java.time.Clock;
import java.util.UUID;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.compartilhado.excecao.AcessoNegadoException;
import br.com.juridora.compartilhado.excecao.ValidacaoException;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.identidade.dominio.CodificadorSenha;
import br.com.juridora.identidade.UsuarioGrupoAlteradoEvent;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.GrupoReservadoAoProprietarioException;
import br.com.juridora.identidade.dominio.PoliticaSenha;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioNaoEncontradoException;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Gestão dos usuários do escritório (HU08, HU10, RF04): cadastrar, consultar, editar, trocar o
 * grupo, desativar e reativar. Tudo roda no tenant do token e sob RLS: usuário de outro
 * escritório não é encontrado e responde 404 (RN02). A permissão ({@code USUARIO_VISUALIZAR}
 * ou {@code USUARIO_GERENCIAR}) é exigida no controller.
 * <p>
 * Regras:
 * <ul>
 *   <li>cadastrar e reativar respeitam o limite de usuários ativos do plano (RN09);</li>
 *   <li>o grupo do usuário, no cadastro e na troca, precisa existir no escritório e não pode
 *       ser o Proprietário, reservado ao dono (HU10);</li>
 *   <li>o proprietário não pode ser desativado (RN03), não sai do grupo Proprietário, e só ele
 *       altera o próprio cadastro;</li>
 *   <li>ninguém desativa a si mesmo nem troca o próprio grupo.</li>
 * </ul>
 * A verificação do grupo trava o registro dele ({@code FOR SHARE}, HU09) até o fim da
 * transação, então ele não é excluído enquanto recebe o usuário.
 */
@Service
public class GestaoUsuariosServico {

	private final UsuarioRepositorio usuarios;
	private final AutorizacaoApi autorizacaoApi;
	private final EscritorioApi escritorioApi;
	private final CodificadorSenha codificadorSenha;
	private final TenantContext tenantContext;
	private final TransactionOperations transacao;
	private final ApplicationEventPublisher eventos;
	private final Clock relogio;

	GestaoUsuariosServico(UsuarioRepositorio usuarios, AutorizacaoApi autorizacaoApi, EscritorioApi escritorioApi,
			CodificadorSenha codificadorSenha, TenantContext tenantContext, TransactionOperations transacao,
			ApplicationEventPublisher eventos, Clock relogio) {
		this.usuarios = usuarios;
		this.autorizacaoApi = autorizacaoApi;
		this.escritorioApi = escritorioApi;
		this.codificadorSenha = codificadorSenha;
		this.tenantContext = tenantContext;
		this.transacao = transacao;
		this.eventos = eventos;
		this.relogio = relogio;
	}

	/**
	 * @throws ValidacaoException se a senha ou o grupo forem inválidos
	 * @throws GrupoReservadoAoProprietarioException se o grupo for o Proprietário
	 * @throws br.com.juridora.identidade.dominio.EmailJaCadastradoException se o e-mail já existir
	 * @throws LimiteDeUsuariosAtingidoException se o plano já tiver o máximo de usuários ativos
	 */
	public DadosUsuario cadastrar(CadastrarUsuarioComando comando) {
		Email email = new Email(comando.email());
		PoliticaSenha.validar(comando.senha());
		// O hash (lento por definição) é calculado fora da transação, sem segurar conexão.
		String senhaHash = codificadorSenha.codificar(comando.senha());
		return transacao.execute(status -> {
			garantirGrupoParaMembro(comando.grupoId());
			garantirVagaNoPlano();
			Usuario novo = usuarios.adicionar(Usuario.membro(comando.nome(), email, senhaHash, comando.grupoId()));
			return DadosUsuario.de(novo);
		});
	}

	@Transactional(readOnly = true)
	public Page<DadosUsuario> listar(Pageable pagina) {
		return usuarios.listar(pagina).map(DadosUsuario::de);
	}

	@Transactional(readOnly = true)
	public DadosUsuario buscar(UUID id) {
		return DadosUsuario.de(encontrar(id));
	}

	@Transactional
	public DadosUsuario alterar(UUID id, AlterarUsuarioComando comando) {
		Usuario usuario = encontrar(id);
		if (usuario.isProprietario() && !ehOUsuarioAtual(usuario)) {
			throw new AcessoNegadoException("Só o proprietário pode alterar o próprio cadastro.");
		}
		usuario.alterar(comando.nome(), new Email(comando.email()));
		return DadosUsuario.de(usuarios.atualizar(usuario));
	}

	/**
	 * Coloca o usuário em outro grupo (HU10). As permissões novas valem a partir do próximo
	 * login (ficam no token, ADR-014). Escolher o grupo atual não é erro e não gera evento.
	 *
	 * @throws UsuarioNaoEncontradoException se o usuário não existir ou for de outro escritório
	 * @throws GrupoReservadoAoProprietarioException se o usuário for o proprietário ou o grupo for o Proprietário
	 * @throws AutoTrocaDeGrupoException se for o próprio usuário de quem executa
	 * @throws ValidacaoException se o grupo não existir no escritório
	 */
	@Transactional
	public DadosUsuario trocarGrupo(UUID id, UUID grupoId) {
		Usuario usuario = encontrar(id);
		if (usuario.isProprietario()) {
			throw GrupoReservadoAoProprietarioException.proprietarioNaoSai();
		}
		if (ehOUsuarioAtual(usuario)) {
			throw new AutoTrocaDeGrupoException();
		}
		UUID anterior = usuario.getGrupoId();
		if (anterior.equals(grupoId)) {
			return DadosUsuario.de(usuario);
		}
		garantirGrupoParaMembro(grupoId);
		usuario.trocarGrupo(grupoId);
		Usuario atualizado = usuarios.atualizar(usuario);
		eventos.publishEvent(new UsuarioGrupoAlteradoEvent(tenantContext.tenantAtual(), id, anterior, grupoId,
				tenantContext.usuarioAtual(), relogio.instant()));
		return DadosUsuario.de(atualizado);
	}

	@Transactional
	public void desativar(UUID id) {
		Usuario usuario = encontrar(id);
		if (!usuario.isProprietario() && ehOUsuarioAtual(usuario)) {
			throw new AutoDesativacaoException();
		}
		usuario.desativar(relogio.instant());
		usuarios.atualizar(usuario);
	}

	@Transactional
	public void reativar(UUID id) {
		Usuario usuario = encontrar(id);
		if (usuario.isAtivo()) {
			return;
		}
		garantirVagaNoPlano();
		usuario.reativar();
		usuarios.atualizar(usuario);
	}

	private Usuario encontrar(UUID id) {
		return usuarios.buscar(id).orElseThrow(UsuarioNaoEncontradoException::new);
	}

	private boolean ehOUsuarioAtual(Usuario usuario) {
		return usuario.getId().equals(tenantContext.usuarioAtual());
	}

	/** Chame dentro da transação que grava o usuário: {@code grupoExiste} trava o grupo até o fim dela. */
	private void garantirGrupoParaMembro(UUID grupoId) {
		if (!autorizacaoApi.grupoExiste(grupoId)) {
			throw new ValidacaoException("O grupo informado não existe.");
		}
		if (autorizacaoApi.ehGrupoProprietario(grupoId)) {
			throw GrupoReservadoAoProprietarioException.membroNaoEntra();
		}
	}

	private void garantirVagaNoPlano() {
		int limite = escritorioApi.limitesDoPlano().usuariosAtivos();
		if (usuarios.contarAtivosComTrava() >= limite) {
			throw new LimiteDeUsuariosAtingidoException(limite);
		}
	}

}
