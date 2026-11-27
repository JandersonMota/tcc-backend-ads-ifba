package br.com.juridora.autorizacao.adaptador.entrada.web;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT /api/grupos/{id}}: o nome novo de um grupo personalizado. */
record RenomearGrupoRequest(
		@Schema(example = "Financeiro e cobrança")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = Grupo.TAMANHO_MAXIMO_NOME, message = "O nome deve ter até {max} caracteres.")
		String nome) {
}
