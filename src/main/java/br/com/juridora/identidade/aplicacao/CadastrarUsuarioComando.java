package br.com.juridora.identidade.aplicacao;

import java.util.UUID;

/** Dados para cadastrar um usuário da equipe (HU08). A senha inicial é definida pelo proprietário. */
public record CadastrarUsuarioComando(String nome, String email, String senha, UUID grupoId) {

	@Override
	public String toString() {
		return "CadastrarUsuarioComando[nome=" + nome + ", grupoId=" + grupoId + "]";
	}

}
