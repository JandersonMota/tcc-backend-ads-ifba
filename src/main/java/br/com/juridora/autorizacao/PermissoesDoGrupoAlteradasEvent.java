package br.com.juridora.autorizacao;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** As permissões de um grupo mudaram (HU09), com os valores antes e depois para a auditoria (HU12). */
public record PermissoesDoGrupoAlteradasEvent(UUID tenantId, UUID grupoId, Set<String> anteriores, Set<String> atuais,
		UUID executadoPor, Instant ocorridoEm) implements EventoDominio {
}
