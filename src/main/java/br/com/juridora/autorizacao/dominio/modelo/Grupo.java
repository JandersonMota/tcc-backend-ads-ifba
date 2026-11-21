package br.com.juridora.autorizacao.dominio.modelo;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import br.com.juridora.compartilhado.dominio.EntidadeTenant;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Agregado Grupo: conjunto de permissões atribuído a usuários do escritório.
 * <p>
 * As permissões ficam em {@code grupo_permissao}; o {@code tenant_id} dessa tabela
 * vem do tenant da transação (DEFAULT na migração V4), pois o Hibernate não aplica
 * {@code @TenantId} em tabelas de coleção.
 */
@Entity
@Table(name = "grupo")
public class Grupo extends EntidadeTenant {

	public static final int TAMANHO_MAXIMO_NOME = 50;

	@Column(nullable = false, length = TAMANHO_MAXIMO_NOME)
	private String nome;

	@Column(nullable = false)
	private boolean padrao;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "grupo_permissao", joinColumns = @JoinColumn(name = "grupo_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "permissao", nullable = false, length = 60)
	private Set<Permissao> permissoes = new HashSet<>();

	protected Grupo() {
	}

	private Grupo(String nome, boolean padrao, Set<Permissao> permissoes) {
		this.nome = nome;
		this.padrao = padrao;
		this.permissoes = new HashSet<>(permissoes);
	}

	/** Grupo padrão criado no cadastro do escritório (RF05). */
	public static Grupo padrao(GrupoPadrao definicao) {
		Objects.requireNonNull(definicao, "definicao é obrigatória");
		return new Grupo(definicao.nome(), true, definicao.permissoes());
	}

	public String getNome() {
		return nome;
	}

	public boolean isPadrao() {
		return padrao;
	}

	public Set<Permissao> getPermissoes() {
		return Set.copyOf(permissoes);
	}

	public boolean possui(Permissao permissao) {
		return permissoes.contains(permissao);
	}

}
