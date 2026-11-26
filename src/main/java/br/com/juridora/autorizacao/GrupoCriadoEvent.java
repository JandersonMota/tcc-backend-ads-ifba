package br.com.juridora.autorizacao;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** Um grupo personalizado foi criado (HU09). Permissões no formato {@code RECURSO_ACAO}. */
public record GrupoCriadoEvent(UUID tenantId, UUID grupoId, String nome, Set<String> permissoes,
		UUID executadoPor, Instant ocorridoEm) implements EventoDominio {
}
