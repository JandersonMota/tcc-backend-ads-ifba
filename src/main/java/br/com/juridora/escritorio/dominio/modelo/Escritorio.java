package br.com.juridora.escritorio.dominio.modelo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agregado Escritório: o próprio tenant.
 * <p>
 * Não estende {@code EntidadeTenant} nem tem {@code @TenantId}: o {@code id} é o tenant.
 * O ID é gerado por quem cadastra, antes da transação (ADR-013).
 */
@Entity
@Table(name = "escritorio")
public class Escritorio {

	@Id
	private UUID id;

	@Column(nullable = false, length = NomeEscritorio.TAMANHO_MAXIMO)
	private String nome;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Plano plano;

	@Column(nullable = false)
	private boolean ativo;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Escritorio() {
	}

	private Escritorio(UUID id, NomeEscritorio nome, Instant agora) {
		this.id = Objects.requireNonNull(id, "id é obrigatório");
		this.nome = nome.valor();
		this.plano = Plano.FREE;
		this.ativo = true;
		this.createdAt = Objects.requireNonNull(agora, "agora é obrigatório");
	}

	/** Novo escritório: nasce no plano Free e ativo (HU05, RN09). */
	public static Escritorio cadastrar(UUID id, NomeEscritorio nome, Instant agora) {
		return new Escritorio(id, Objects.requireNonNull(nome, "nome é obrigatório"), agora);
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public Plano getPlano() {
		return plano;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
