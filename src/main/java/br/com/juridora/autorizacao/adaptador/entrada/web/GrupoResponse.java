package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.UUID;

import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarGruposCasoDeUso.GrupoResumo;

/** Grupo na listagem: {@code padrao} indica os grupos criados no cadastro do escritório. */
record GrupoResponse(UUID id, String nome, boolean padrao) {

	static GrupoResponse de(GrupoResumo grupo) {
		return new GrupoResponse(grupo.id(), grupo.nome(), grupo.padrao());
	}

}
