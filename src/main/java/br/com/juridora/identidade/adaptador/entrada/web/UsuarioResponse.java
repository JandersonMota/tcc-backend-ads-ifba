package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.UUID;

import br.com.juridora.identidade.aplicacao.DadosUsuario;

/** Usuário do escritório nas respostas de {@code /api/usuarios}. Nunca inclui a senha. */
record UsuarioResponse(UUID id, String nome, String email, UUID grupoId, boolean proprietario, boolean ativo) {

	static UsuarioResponse de(DadosUsuario usuario) {
		return new UsuarioResponse(usuario.id(), usuario.nome(), usuario.email(), usuario.grupoId(),
				usuario.proprietario(), usuario.ativo());
	}

}
