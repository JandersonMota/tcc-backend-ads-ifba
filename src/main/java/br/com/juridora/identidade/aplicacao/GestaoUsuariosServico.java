package br.com.juridora.identidade.aplicacao;

import java.time.Clock;
import java.util.UUID;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.compartilhado.excecao.AcessoNegadoException;
import br.com.juridora.compartilhado.excecao.ValidacaoException;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.identidade.dominio.CodificadorSenha;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.PoliticaSenha;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioNaoEncontradoException;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Gestão dos usuários do escritório (HU08, RF04): cadastrar, consultar, editar, desativar
 * e reativar. Tudo roda no tenant do token e sob RLS: usuário de outro escritório não é
 * encontrado e responde 404 (RN02). A permissão ({@code USUARIO_VISUALIZAR} ou
 * {@code USUARIO_GERENCIAR}) é exigida no controller.
 * <p>
 * Regras:
 * <ul>
 *   <li>cadastrar e reativar respeitam o limite de usuários ativos do plano (RN09);</li>
 *   <li>o grupo do usuário novo precisa existir no escritório;</li>
 *   <li>o proprietário não pode ser desativado (RN03), e só ele altera o próprio cadastro;</li>
 *   <li>ninguém desativa a si mesmo.</li>
 * </ul>
 */
@Service
public class GestaoUsuariosServico {

	private final UsuarioRepositorio usuarios;
	private final AutorizacaoApi autorizacaoApi;
	private final EscritorioApi escritorioApi;
	private final CodificadorSenha codificadorSenha;
	private final TenantContext tenantContext;
	private final TransactionOperations transacao;
	private final Clock relogio;

	GestaoUsuariosServico(UsuarioRepositorio usuarios, AutorizacaoApi autorizacaoApi, EscritorioApi escritorioApi,
			CodificadorSenha codificadorSenha, TenantContext tenantContext, TransactionOperations transacao,
			Clock relogio) {
		this.usuarios = usuarios;
		this.autorizacaoApi = autorizacaoApi;
		this.escritorioApi = escritorioApi;
		this.codificadorSenha = codificadorSenha;
		this.tenantContext = tenantContext;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * @throws ValidacaoException se a senha ou o grupo forem inválidos
	 * @throws br.com.juridora.identidade.dominio.EmailJaCadastradoException se o e-mail já existir
	 * @throws LimiteDeUsuariosAtingidoException se o plano já tiver o máximo de usuários ativos
	 */
	public DadosUsuario cadastrar(CadastrarUsuarioComando comando) {
		Email email = new Email(comando.email());
		PoliticaSenha.validar(comando.senha());
		// O hash (lento por definição) é calculado fora da transação, sem segurar conexão.
		String senhaHash = codificadorSenha.codificar(comando.senha());
		return transacao.execute(status -> {
			if (!autorizacaoApi.grupoExiste(comando.grupoId())) {
				throw new ValidacaoException("O grupo informado não existe.");
			}
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

	private void garantirVagaNoPlano() {
		int limite = escritorioApi.limitesDoPlano().usuariosAtivos();
		if (usuarios.contarAtivosComTrava() >= limite) {
			throw new LimiteDeUsuariosAtingidoException(limite);
		}
	}

}
