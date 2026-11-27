package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.aplicacao.consulta.DetalheGrupo;

/** Grupo com as permissões, na ordem do catálogo. */
record DetalheGrupoResponse(UUID id, String nome, boolean padrao, List<String> permissoes) {

	static DetalheGrupoResponse de(DetalheGrupo grupo) {
		return new DetalheGrupoResponse(grupo.id(), grupo.nome(), grupo.padrao(), grupo.permissoes());
	}

}
