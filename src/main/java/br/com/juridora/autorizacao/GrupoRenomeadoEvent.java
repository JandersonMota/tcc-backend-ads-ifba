package br.com.juridora.autorizacao;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** Um grupo personalizado mudou de nome (HU09). */
public record GrupoRenomeadoEvent(UUID tenantId, UUID grupoId, String nomeAnterior, String nomeNovo,
		UUID executadoPor, Instant ocorridoEm) implements EventoDominio {
}
