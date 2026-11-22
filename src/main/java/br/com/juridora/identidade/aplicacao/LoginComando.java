package br.com.juridora.identidade.aplicacao;

/** Credenciais informadas no login (HU06). Sem tenant: ele vem do usuário encontrado. */
public record LoginComando(String email, String senha) {

	@Override
	public String toString() {
		// nunca expor a senha nem o e-mail em logs
		return "LoginComando[]";
	}

}
