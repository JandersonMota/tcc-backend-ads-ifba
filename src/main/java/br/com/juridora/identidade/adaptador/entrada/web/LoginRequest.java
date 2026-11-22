package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.LoginComando;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de {@code POST /api/autenticacao/login}. Só confere se os campos vieram: as
 * regras de tamanho da senha não são repetidas aqui, para o erro não dar pistas.
 */
record LoginRequest(
		@Schema(example = "ana@silva.adv.br")
		@NotBlank(message = "O e-mail é obrigatório.")
		@Email(message = "O e-mail é inválido.")
		String email,

		@Schema(example = "senha-forte-123", format = "password")
		@NotBlank(message = "A senha é obrigatória.")
		String senha) {

	LoginComando paraComando() {
		return new LoginComando(email, senha);
	}

	@Override
	public String toString() {
		// nunca expor a senha nem o e-mail em logs
		return "LoginRequest[]";
	}

}
