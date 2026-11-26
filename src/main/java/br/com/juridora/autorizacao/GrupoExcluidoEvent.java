package br.com.juridora.autorizacao;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** Um grupo personalizado sem usuários foi excluído (HU09, RN07). */
public record GrupoExcluidoEvent(UUID tenantId, UUID grupoId, String nome,
		UUID executadoPor, Instant ocorridoEm) implements EventoDominio {
}
