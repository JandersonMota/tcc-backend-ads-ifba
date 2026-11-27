package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.AlterarUsuarioComando;
import br.com.juridora.identidade.dominio.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT /api/usuarios/{id}}: nome e e-mail (que também é o login). */
record AlterarUsuarioRequest(
		@Schema(example = "Bruno Costa")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = Usuario.TAMANHO_MAXIMO_NOME, message = "O nome deve ter até {max} caracteres.")
		String nome,

		@Schema(example = "bruno@silva.adv.br")
		@NotBlank(message = "O e-mail é obrigatório.")
		@Email(message = "O e-mail é inválido.")
		@Size(max = br.com.juridora.identidade.dominio.Email.TAMANHO_MAXIMO,
				message = "O e-mail deve ter até {max} caracteres.")
		String email) {

	AlterarUsuarioComando paraComando() {
		return new AlterarUsuarioComando(nome, email);
	}

}
