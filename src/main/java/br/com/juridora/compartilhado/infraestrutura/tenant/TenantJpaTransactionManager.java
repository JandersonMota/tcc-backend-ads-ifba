package br.com.juridora.compartilhado.infraestrutura.tenant;

import java.sql.PreparedStatement;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Camada 2 do isolamento (ADR-003): no início de cada transação, define
 * {@code app.tenant_id} no PostgreSQL, lido pelas políticas de RLS.
 * <p>
 * {@code set_config(..., true)} vale só para a transação: ao terminar, a conexão
 * volta ao pool sem tenant. Sem tenant definido, grava {@code ''} e as políticas
 * não retornam nenhuma linha (falha segura).
 * <p>
 * Consultas JDBC ({@code JdbcTemplate}) executadas dentro da transação usam a mesma
 * conexão e também ficam sujeitas ao RLS.
 */
class TenantJpaTransactionManager extends JpaTransactionManager {

	private static final String DEFINIR_TENANT = "select set_config('app.tenant_id', ?, true)";

	private final transient TenantContext tenantContext;

	TenantJpaTransactionManager(EntityManagerFactory entityManagerFactory, TenantContext tenantContext) {
		super(entityManagerFactory);
		this.tenantContext = tenantContext;
	}

	@Override
	protected void doBegin(Object transacao, TransactionDefinition definicao) {
		super.doBegin(transacao, definicao);
		String tenant = tenantContext.tenantOpcional().map(Object::toString).orElse("");
		var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
		holder.getEntityManager().unwrap(Session.class).doWork(conexao -> {
			try (PreparedStatement comando = conexao.prepareStatement(DEFINIR_TENANT)) {
				comando.setString(1, tenant);
				comando.execute();
			}
		});
	}

}
