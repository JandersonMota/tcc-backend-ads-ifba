package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.UUID;

import br.com.juridora.identidade.aplicacao.CadastrarUsuarioComando;
import br.com.juridora.identidade.dominio.PoliticaSenha;
import br.com.juridora.identidade.dominio.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/usuarios}. A senha inicial é definida por quem cadastra (não
 * há envio de e-mail no MVP). Não tem {@code tenantId} nem {@code proprietario}: o usuário
 * entra no escritório do token e nunca como proprietário.
 */
record CadastrarUsuarioRequest(
		@Schema(example = "Bruno Costa")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = Usuario.TAMANHO_MAXIMO_NOME, message = "O nome deve ter até {max} caracteres.")
		String nome,

		@Schema(example = "bruno@silva.adv.br")
		@NotBlank(message = "O e-mail é obrigatório.")
		@Email(message = "O e-mail é inválido.")
		@Size(max = br.com.juridora.identidade.dominio.Email.TAMANHO_MAXIMO,
				message = "O e-mail deve ter até {max} caracteres.")
		String email,

		@Schema(example = "senha-inicial-123", format = "password")
		@NotBlank(message = "A senha é obrigatória.")
		@Size(min = PoliticaSenha.TAMANHO_MINIMO, max = PoliticaSenha.TAMANHO_MAXIMO,
				message = "A senha deve ter de {min} a {max} caracteres.")
		String senha,

		@Schema(description = "Grupo do escritório (GET /api/grupos)")
		@NotNull(message = "O grupo é obrigatório.")
		UUID grupoId) {

	CadastrarUsuarioComando paraComando() {
		return new CadastrarUsuarioComando(nome, email, senha, grupoId);
	}

	@Override
	public String toString() {
		return "CadastrarUsuarioRequest[nome=" + nome + ", grupoId=" + grupoId + "]";
	}

}
