package br.com.juridora.compartilhado.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base das entidades de negócio: toda tabela de negócio tem {@code tenant_id}
 * (ADR-002) e as colunas de controle {@code created_at}, {@code updated_at} e
 * {@code created_by} (Modelo de Dados).
 * <p>
 * {@code tenantId} é preenchido pelo Hibernate a partir do tenant da execução
 * ({@code @TenantId}) e nunca muda; não há setter, e nenhum DTO de entrada o recebe.
 * As colunas de controle são preenchidas pela infraestrutura ao gravar.
 */
@MappedSuperclass
public abstract class EntidadeTenant {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	protected EntidadeTenant() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	@Override
	public final boolean equals(Object outro) {
		if (this == outro) {
			return true;
		}
		return outro instanceof EntidadeTenant entidade
				&& getClass() == entidade.getClass()
				&& id != null && id.equals(entidade.id);
	}

	@Override
	public final int hashCode() {
		return getClass().hashCode();
	}

}
