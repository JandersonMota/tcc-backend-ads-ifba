package br.com.juridora.identidade.adaptador.entrada.web;

import br.com.juridora.identidade.aplicacao.CadastrarEscritorioComando;
import br.com.juridora.identidade.aplicacao.CadastroEscritorioServico;
import br.com.juridora.identidade.dominio.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/autenticacao/cadastro}. Não tem {@code tenantId}: o tenant
 * é gerado pelo sistema, e um {@code tenantId} enviado no JSON é ignorado.
 */
record CadastrarEscritorioRequest(
		@Schema(example = "Silva & Associados")
		@NotBlank(message = "O nome do escritório é obrigatório.")
		// mesmo limite de NomeEscritorio, que é interno ao módulo escritorio e não pode ser importado
		@Size(max = 200, message = "O nome do escritório deve ter até {max} caracteres.")
		String nomeEscritorio,

		@Schema(example = "Ana Silva")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = Usuario.TAMANHO_MAXIMO_NOME, message = "O nome deve ter até {max} caracteres.")
		String nome,

		@Schema(example = "ana@silva.adv.br")
		@NotBlank(message = "O e-mail é obrigatório.")
		@Email(message = "O e-mail é inválido.")
		@Size(max = br.com.juridora.identidade.dominio.Email.TAMANHO_MAXIMO,
				message = "O e-mail deve ter até {max} caracteres.")
		String email,

		@Schema(example = "senha-forte-123", format = "password")
		@NotBlank(message = "A senha é obrigatória.")
		@Size(min = CadastroEscritorioServico.TAMANHO_MINIMO_SENHA, max = CadastroEscritorioServico.TAMANHO_MAXIMO_SENHA,
				message = "A senha deve ter de {min} a {max} caracteres.")
		String senha) {

	CadastrarEscritorioComando paraComando() {
		return new CadastrarEscritorioComando(nomeEscritorio, nome, email, senha);
	}

	@Override
	public String toString() {
		return "CadastrarEscritorioRequest[nomeEscritorio=" + nomeEscritorio + ", nome=" + nome + "]";
	}

}
