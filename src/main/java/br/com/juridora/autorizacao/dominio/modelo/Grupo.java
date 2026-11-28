package br.com.juridora.autorizacao.dominio.modelo;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import br.com.juridora.compartilhado.dominio.EntidadeTenant;
import br.com.juridora.compartilhado.excecao.ValidacaoException;
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
 * Regras (HU09):
 * <ul>
 *   <li>grupos padrão (RF05) e personalizados são renomeados e excluídos da mesma forma; a
 *       exclusão exige grupo sem usuários (RN07), o que o caso de uso verifica;</li>
 *   <li>o grupo Proprietário é a exceção (RN04): mantém o catálogo completo e não é renomeado
 *       nem excluído;</li>
 *   <li>o nome é obrigatório, tem até {@value #TAMANHO_MAXIMO_NOME} caracteres e é único no
 *       escritório sem diferenciar maiúsculas (índice {@code ux_grupo_tenant_nome}, V8).</li>
 * </ul>
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

	/** Grupo criado pelo proprietário (HU09, RF05). Pode nascer sem permissões. */
	public static Grupo personalizado(String nome, Set<Permissao> permissoes) {
		Objects.requireNonNull(permissoes, "permissoes é obrigatório");
		return new Grupo(validarNome(nome), false, permissoes);
	}

	/** @throws GrupoProprietarioProtegidoException se for o grupo Proprietário */
	public void renomear(String novoNome) {
		if (ehProprietario()) {
			throw new GrupoProprietarioProtegidoException("O grupo Proprietário não pode ser renomeado.");
		}
		this.nome = validarNome(novoNome);
	}

	/**
	 * Substitui as permissões do grupo pelo conjunto informado (vazio é permitido).
	 *
	 * @throws GrupoProprietarioProtegidoException se for o grupo Proprietário (RN04)
	 */
	public void definirPermissoes(Set<Permissao> novasPermissoes) {
		Objects.requireNonNull(novasPermissoes, "novasPermissoes é obrigatório");
		if (ehProprietario()) {
			throw new GrupoProprietarioProtegidoException(
					"As permissões do grupo Proprietário não podem ser alteradas: ele tem todas as permissões.");
		}
		permissoes.clear();
		permissoes.addAll(novasPermissoes);
	}

	/**
	 * Verifica a parte da RN07 que cabe ao agregado; "sem usuários" depende de dados da
	 * identidade e fica no caso de uso.
	 *
	 * @throws GrupoProprietarioProtegidoException se for o grupo Proprietário
	 */
	public void garantirExcluivel() {
		if (ehProprietario()) {
			throw new GrupoProprietarioProtegidoException("O grupo Proprietário não pode ser excluído.");
		}
	}

	/**
	 * O grupo padrão Proprietário. Ele não é renomeado nem excluído e o nome é único no escritório
	 * sem diferenciar maiúsculas, então nenhum outro grupo padrão renomeado assume esse nome.
	 */
	public boolean ehProprietario() {
		return padrao && GrupoPadrao.PROPRIETARIO.nome().equals(nome);
	}

	public String getNome() {
		return nome;
	}

	public boolean isPadrao() {
		return padrao;
	}

	/**
	 * Permissões efetivas do grupo. O Proprietário tem sempre o catálogo inteiro (RN04),
	 * mesmo que o conjunto gravado seja mais antigo: uma permissão nova no catálogo chega ao
	 * dono sem migração de dados (HU11).
	 */
	public Set<Permissao> getPermissoes() {
		if (ehProprietario()) {
			return Set.copyOf(EnumSet.allOf(Permissao.class));
		}
		return Set.copyOf(permissoes);
	}

	public boolean possui(Permissao permissao) {
		return getPermissoes().contains(permissao);
	}

	private static String validarNome(String nome) {
		String limpo = nome == null ? "" : nome.strip();
		if (limpo.isEmpty()) {
			throw new ValidacaoException("O nome do grupo é obrigatório.");
		}
		if (limpo.length() > TAMANHO_MAXIMO_NOME) {
			throw new ValidacaoException("O nome do grupo deve ter até " + TAMANHO_MAXIMO_NOME + " caracteres.");
		}
		return limpo;
	}

}
