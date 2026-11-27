package br.com.juridora.autorizacao.aplicacao.consulta;

import java.util.List;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.Permissao;

/** Grupo com as suas permissões, na ordem do catálogo (HU09). */
public record DetalheGrupo(UUID id, String nome, boolean padrao, List<String> permissoes) {

	public static DetalheGrupo de(Grupo grupo) {
		return new DetalheGrupo(grupo.getId(), grupo.getNome(), grupo.isPadrao(),
				Permissao.codigos(grupo.getPermissoes()));
	}

}
