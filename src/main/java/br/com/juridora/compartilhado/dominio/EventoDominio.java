package br.com.juridora.compartilhado.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio publicado entre módulos (ADR-005).
 * <p>
 * Carrega o tenant de origem: o listener o usa para definir o tenant
 * ({@code TenantContext#executarComo}) antes de abrir a sua transação.
 */
public interface EventoDominio {

	UUID tenantId();

	Instant ocorridoEm();

}
