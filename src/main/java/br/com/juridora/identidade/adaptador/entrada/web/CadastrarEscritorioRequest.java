package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.CadastrarEscritorioComando;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/autenticacao/cadastro}. Não tem {@code tenantId}: o tenant
 * é gerado pelo sistema, e um {@code tenantId} enviado no JSON é ignorado.
 */
record CadastrarEscritorioRequest(
		@NotBlank(message = "O nome do escritório é obrigatório.")
		@Size(max = 200, message = "O nome do escritório deve ter até 200 caracteres.")
		String nomeEscritorio,

		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = 200, message = "O nome deve ter até 200 caracteres.")
		String nome,

		@NotBlank(message = "O e-mail é obrigatório.")
		@Email(message = "O e-mail é inválido.")
		@Size(max = 254, message = "O e-mail deve ter até 254 caracteres.")
		String email,

		@NotBlank(message = "A senha é obrigatória.")
		@Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres.")
		String senha) {

	CadastrarEscritorioComando paraComando() {
		return new CadastrarEscritorioComando(nomeEscritorio, nome, email, senha);
	}

	@Override
	public String toString() {
		return "CadastrarEscritorioRequest[nomeEscritorio=" + nomeEscritorio + ", nome=" + nome + "]";
	}

}
