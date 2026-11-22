package br.com.juridora.identidade.aplicacao;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.identidade.dominio.CodificadorSenha;
import br.com.juridora.identidade.dominio.CredencialLogin;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.EmissorToken;
import br.com.juridora.identidade.dominio.EmissorToken.TokenAcesso;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.springframework.stereotype.Service;

/**
 * Login com e-mail e senha (HU06, RF02) – operação pública, sem JWT.
 * <ol>
 *   <li>e-mail bloqueado por excesso de falhas → 429, sem conferir a senha (critério 3);</li>
 *   <li>busca a credencial pelo e-mail, sem tenant ({@code credencial_por_email}, ADR-015);</li>
 *   <li>confere a senha; senha errada, e-mail inexistente e usuário desativado dão o mesmo
 *       erro (critério 2), e o BCrypt roda mesmo sem usuário, para o tempo de resposta não
 *       revelar se o e-mail existe;</li>
 *   <li>já com o tenant do usuário, lê as permissões do grupo e emite o token (critério 1).</li>
 * </ol>
 * Não transacional, como o cadastro (ADR-013): o tenant é definido antes de abrir as
 * transações de leitura.
 */
@Service
public class LoginServico {

	private final UsuarioRepositorio usuarios;

	private final CodificadorSenha codificadorSenha;

	private final ControleTentativasLogin tentativas;

	private final EmissorToken emissorToken;

	private final AutorizacaoApi autorizacaoApi;

	private final TenantContext tenantContext;

	/** Hash conferido quando o e-mail não existe, para gastar o mesmo tempo de um login real. */
	private final String hashFicticio;

	LoginServico(UsuarioRepositorio usuarios, CodificadorSenha codificadorSenha, ControleTentativasLogin tentativas,
			EmissorToken emissorToken, AutorizacaoApi autorizacaoApi, TenantContext tenantContext) {
		this.usuarios = usuarios;
		this.codificadorSenha = codificadorSenha;
		this.tentativas = tentativas;
		this.emissorToken = emissorToken;
		this.autorizacaoApi = autorizacaoApi;
		this.tenantContext = tenantContext;
		this.hashFicticio = codificadorSenha.codificar("senha-ficticia-para-tempo-constante");
	}

	public TokenAcesso entrar(LoginComando comando) {
		Email email = new Email(comando.email());
		String chave = email.valor();
		tentativas.verificarLiberado(chave);

		Optional<CredencialLogin> credencial = usuarios.credencialPorEmail(email);
		boolean senhaConfere = senhaConfere(comando.senha(), credencial.map(CredencialLogin::senhaHash).orElse(hashFicticio));
		if (credencial.isEmpty() || !senhaConfere || !credencial.get().ativo()) {
			tentativas.registrarFalha(chave);
			throw new CredenciaisInvalidasException();
		}
		tentativas.registrarSucesso(chave);

		CredencialLogin encontrada = credencial.get();
		return tenantContext.executarComo(encontrada.tenantId(), encontrada.usuarioId(), () -> emitirToken(encontrada));
	}

	private boolean senhaConfere(String senha, String hash) {
		// O BCrypt recusa mais de 72 bytes; uma senha assim nunca foi cadastrada (HU05).
		if (senha == null || senha.getBytes(StandardCharsets.UTF_8).length > CadastroEscritorioServico.TAMANHO_MAXIMO_SENHA) {
			codificadorSenha.confere("", hashFicticio);
			return false;
		}
		return codificadorSenha.confere(senha, hash);
	}

	private TokenAcesso emitirToken(CredencialLogin credencial) {
		Usuario usuario = usuarios.buscar(credencial.usuarioId()).orElseThrow(CredenciaisInvalidasException::new);
		Set<String> permissoes = autorizacaoApi.permissoesDoGrupo(usuario.getGrupoId());
		return emissorToken.emitir(usuario.getId(), usuario.getTenantId(), usuario.isProprietario(), permissoes);
	}

}
