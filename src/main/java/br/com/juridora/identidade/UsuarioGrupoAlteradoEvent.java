package br.com.juridora.identidade;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** Um usuário passou para outro grupo e recebe as permissões dele no próximo login (HU10). */
public record UsuarioGrupoAlteradoEvent(UUID tenantId, UUID usuarioId, UUID grupoAnteriorId, UUID grupoNovoId,
		UUID executadoPor, Instant ocorridoEm) implements EventoDominio {
}
