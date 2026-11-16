package br.com.juridora.compartilhado.infraestrutura.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tenant (escritório) e usuário da execução atual (ADR-012).
 * <p>
 * Os valores ficam em um {@link ScopedValue}: valem só dentro de
 * {@link #executarComo}, não vazam entre requisições nem entre threads e não
 * podem ser alterados no meio da execução. Quem define o tenant é sempre um
 * adaptador de entrada confiável:
 * <ul>
 *   <li>requisição HTTP: filtro do Spring Security, a partir da claim {@code tenant_id}
 *       do JWT validado (HU06); nunca de parâmetro, header ou corpo (RNF02);</li>
 *   <li>listener de evento: a partir de {@code EventoDominio#tenantId()};</li>
 *   <li>testes: diretamente.</li>
 * </ul>
 * O tenant deve ser definido <strong>antes</strong> de abrir a transação: é no
 * início dela que o Hibernate fixa o tenant da sessão ({@code @TenantId}) e que
 * {@link TenantJpaTransactionManager} executa {@code set_config('app.tenant_id', ...)} (RLS).
 */
@Component
public class TenantContext {

	private static final ScopedValue<Identificacao> ATUAL = ScopedValue.newInstance();

	/** Tenant e usuário de uma execução; o usuário é opcional (ex.: tarefas do sistema). */
	public record Identificacao(UUID tenantId, UUID usuarioId) {
		public Identificacao {
			Objects.requireNonNull(tenantId, "tenantId é obrigatório");
		}
	}

	/** Tenant da execução atual. */
	public UUID tenantAtual() {
		return tenantOpcional().orElseThrow(TenantNaoDefinidoException::new);
	}

	/** Tenant da execução atual, se houver. */
	public Optional<UUID> tenantOpcional() {
		return ATUAL.isBound() ? Optional.of(ATUAL.get().tenantId()) : Optional.empty();
	}

	/** Usuário da execução atual. */
	public UUID usuarioAtual() {
		return usuarioOpcional().orElseThrow(() -> new IllegalStateException("Nenhum usuário definido na execução atual"));
	}

	/** Usuário da execução atual, se houver. */
	public Optional<UUID> usuarioOpcional() {
		return ATUAL.isBound() ? Optional.ofNullable(ATUAL.get().usuarioId()) : Optional.empty();
	}

	/** Executa {@code acao} com o tenant e o usuário informados. */
	public void executarComo(UUID tenantId, UUID usuarioId, Runnable acao) {
		ScopedValue.where(ATUAL, identificar(tenantId, usuarioId)).run(acao);
	}

	/** Executa {@code acao} com o tenant e o usuário informados e devolve o resultado. */
	public <T> T executarComo(UUID tenantId, UUID usuarioId, Supplier<T> acao) {
		return ScopedValue.where(ATUAL, identificar(tenantId, usuarioId)).call(acao::get);
	}

	private static Identificacao identificar(UUID tenantId, UUID usuarioId) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			// A sessão do Hibernate e o app.tenant_id já foram fixados no início da transação:
			// trocar o tenant agora não teria efeito no banco. Falha cedo em vez de vazar.
			throw new IllegalStateException(
					"O tenant deve ser definido antes de abrir a transação (ex.: listener com @Transactional)");
		}
		return new Identificacao(tenantId, usuarioId);
	}

}
