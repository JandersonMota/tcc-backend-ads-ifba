package br.com.juridora.compartilhado.infraestrutura.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Substitui o gerenciador de transações JPA do Spring Boot pelo que define
 * {@code app.tenant_id} no início de cada transação (RLS).
 */
@Configuration(proxyBeanMethods = false)
class TenantConfiguracao {

	@Bean
	PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
			TenantContext tenantContext, ObjectProvider<TransactionManagerCustomizers> customizadores) {
		var gerenciador = new TenantJpaTransactionManager(entityManagerFactory, tenantContext);
		customizadores.ifAvailable(c -> c.customize(gerenciador));
		return gerenciador;
	}

}
