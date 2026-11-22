package br.com.juridora.identidade.aplicacao;

import java.util.List;
import java.util.UUID;

/** Dados e permissões efetivas do usuário autenticado ({@code GET /api/perfil}). */
public record Perfil(UUID id, String nome, String email, UUID escritorioId, boolean proprietario,
		List<String> permissoes) {

	public Perfil {
		permissoes = List.copyOf(permissoes);
	}

}
