package br.com.juridora.identidade.aplicacao;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.springframework.stereotype.Service;

/**
 * Perfil do usuário autenticado. Usuário e tenant vêm do {@link TenantContext}, definido
 * a partir do JWT; a leitura roda sob RLS, então só enxerga o próprio escritório.
 * <p>
 * As permissões são lidas do banco, não do token: refletem mudanças feitas depois do login.
 */
@Service
public class PerfilServico {

	private final UsuarioRepositorio usuarios;

	private final AutorizacaoApi autorizacaoApi;

	private final TenantContext tenantContext;

	PerfilServico(UsuarioRepositorio usuarios, AutorizacaoApi autorizacaoApi, TenantContext tenantContext) {
		this.usuarios = usuarios;
		this.autorizacaoApi = autorizacaoApi;
		this.tenantContext = tenantContext;
	}

	/**
	 * @throws UsuarioDoTokenInvalidoException se o usuário do token não existir mais ou estiver desativado
	 */
	public Perfil perfilAtual() {
		Usuario usuario = usuarios.buscar(tenantContext.usuarioAtual())
				.filter(Usuario::isAtivo)
				.orElseThrow(UsuarioDoTokenInvalidoException::new);
		return new Perfil(usuario.getId(), usuario.getNome(), usuario.getEmail().valor(), usuario.getTenantId(),
				usuario.isProprietario(), autorizacaoApi.permissoesDoGrupo(usuario.getGrupoId()).stream().sorted().toList());
	}

}
