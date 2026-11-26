package br.com.juridora.autorizacao.adaptador.entrada.web;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de {@code PUT /api/grupos/{id}/permissoes}: o conjunto completo, que substitui o
 * atual (lista vazia retira todas as permissões).
 */
record DefinirPermissoesRequest(
		@Schema(description = "Códigos de GET /api/permissoes", example = "[\"CLIENTE_VISUALIZAR\", \"PROCESSO_VISUALIZAR\"]")
		@NotNull(message = "As permissões são obrigatórias (use [] para nenhuma).")
		Set<@NotBlank(message = "A permissão não pode ser vazia.") String> permissoes) {
}
