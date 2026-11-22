package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.List;
import java.util.UUID;

import br.com.juridora.identidade.aplicacao.Perfil;

/** Resposta de {@code GET /api/perfil}. */
record PerfilResponse(UUID id, String nome, String email, UUID escritorioId, boolean proprietario,
		List<String> permissoes) {

	static PerfilResponse de(Perfil perfil) {
		return new PerfilResponse(perfil.id(), perfil.nome(), perfil.email(), perfil.escritorioId(),
				perfil.proprietario(), perfil.permissoes());
	}

}
