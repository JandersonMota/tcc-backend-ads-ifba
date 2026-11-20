package br.com.juridora.identidade.aplicacao;

import java.util.UUID;

/** Escritório e Proprietário criados no cadastro. */
public record ResultadoCadastro(UUID escritorioId, UUID usuarioId) {
}
