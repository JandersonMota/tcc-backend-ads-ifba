package br.com.juridora.identidade.aplicacao;

import java.util.UUID;

import br.com.juridora.identidade.dominio.Usuario;

/** Usuário como a gestão de usuários o expõe (HU08), sem a senha. */
public record DadosUsuario(UUID id, String nome, String email, UUID grupoId, boolean proprietario, boolean ativo) {

	static DadosUsuario de(Usuario usuario) {
		return new DadosUsuario(usuario.getId(), usuario.getNome(), usuario.getEmail().valor(), usuario.getGrupoId(),
				usuario.isProprietario(), usuario.isAtivo());
	}

}
