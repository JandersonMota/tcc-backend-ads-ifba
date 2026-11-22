package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.dominio.EmissorToken.TokenAcesso;

/**
 * Resposta do login: o token vai no cabeçalho {@code Authorization: Bearer <token>} das
 * próximas requisições; {@code expiraEm} é a validade em segundos.
 */
record LoginResponse(String token, String tipo, long expiraEm) {

	static LoginResponse de(TokenAcesso token) {
		return new LoginResponse(token.valor(), "Bearer", token.validade().toSeconds());
	}

	@Override
	public String toString() {
		// nunca expor o token em logs
		return "LoginResponse[tipo=" + tipo + ", expiraEm=" + expiraEm + "]";
	}

}
