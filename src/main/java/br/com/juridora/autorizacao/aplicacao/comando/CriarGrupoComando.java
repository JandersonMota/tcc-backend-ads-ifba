package br.com.juridora.autorizacao.aplicacao.comando;

import java.util.Set;

/** Grupo personalizado novo (HU09); permissões no formato {@code RECURSO_ACAO}. */
public record CriarGrupoComando(String nome, Set<String> permissoes) {

	public CriarGrupoComando {
		permissoes = permissoes == null ? Set.of() : Set.copyOf(permissoes);
	}

}
