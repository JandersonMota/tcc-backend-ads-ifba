package br.com.juridora.compartilhado.infraestrutura.tenant;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Camada 1 do isolamento (ADR-002): informa ao Hibernate o tenant da sessão, usado
 * pelos campos {@code @TenantId} para preencher {@code tenant_id} nas inserções e
 * filtrar as consultas.
 * <p>
 * Sem tenant definido, devolve {@link #SEM_TENANT}, que não corresponde a nenhum
 * escritório: consultas não retornam linhas e inserções são rejeitadas pela FK e
 * pelo RLS (falha segura).
 */
@Component
class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

	/** Tenant inexistente usado quando a execução não tem tenant. */
	static final UUID SEM_TENANT = new UUID(0L, 0L);

	private final TenantContext tenantContext;

	TenantIdentifierResolver(TenantContext tenantContext) {
		this.tenantContext = tenantContext;
	}

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return tenantContext.tenantOpcional().orElse(SEM_TENANT);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	@Override
	public void customize(Map<String, Object> propriedades) {
		propriedades.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}

}
