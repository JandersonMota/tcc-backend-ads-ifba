package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.Set;

import br.com.juridora.autorizacao.aplicacao.comando.CriarGrupoComando;
import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/grupos}: nome e permissões iniciais (opcionais). */
record CriarGrupoRequest(
		@Schema(example = "Financeiro")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = Grupo.TAMANHO_MAXIMO_NOME, message = "O nome deve ter até {max} caracteres.")
		String nome,

		@Schema(description = "Códigos de GET /api/permissoes", example = "[\"FINANCEIRO_VISUALIZAR\", \"FINANCEIRO_CRIAR\"]")
		Set<@NotBlank(message = "A permissão não pode ser vazia.") String> permissoes) {

	CriarGrupoComando paraComando() {
		return new CriarGrupoComando(nome, permissoes);
	}

}
