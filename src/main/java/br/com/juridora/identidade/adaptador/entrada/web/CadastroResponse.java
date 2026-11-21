package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.UUID;

/** Resposta do cadastro. O token só é emitido no login (HU06). */
record CadastroResponse(UUID escritorioId, UUID usuarioId) {
}
