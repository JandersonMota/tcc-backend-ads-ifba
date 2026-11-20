package br.com.juridora.identidade.dominio;

import java.util.Objects;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EntidadeTenant;
import br.com.juridora.compartilhado.excecao.ValidacaoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Usuário do escritório. O e-mail é o login e é único na plataforma.
 * <p>
 * {@code grupoId} referencia um grupo do módulo autorizacao apenas pelo ID.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadeTenant {

	public static final int TAMANHO_MAXIMO_NOME = 200;

	@Column(name = "grupo_id", nullable = false)
	private UUID grupoId;

	@Column(nullable = false, length = TAMANHO_MAXIMO_NOME)
	private String nome;

	@Column(nullable = false, length = Email.TAMANHO_MAXIMO)
	private String email;

	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Column(nullable = false)
	private boolean proprietario;

	@Column(nullable = false)
	private boolean ativo;

	protected Usuario() {
	}

	private Usuario(String nome, Email email, String senhaHash, UUID grupoId, boolean proprietario) {
		if (nome == null || nome.isBlank()) {
			throw new ValidacaoException("O nome é obrigatório.");
		}
		this.nome = nome.strip();
		if (this.nome.length() > TAMANHO_MAXIMO_NOME) {
			throw new ValidacaoException("O nome deve ter até " + TAMANHO_MAXIMO_NOME + " caracteres.");
		}
		this.email = Objects.requireNonNull(email, "email é obrigatório").valor();
		this.senhaHash = Objects.requireNonNull(senhaHash, "senhaHash é obrigatório");
		this.grupoId = Objects.requireNonNull(grupoId, "grupoId é obrigatório");
		this.proprietario = proprietario;
		this.ativo = true;
	}

	/** Proprietário criado no cadastro do escritório (RN03), já ativo. */
	public static Usuario proprietario(String nome, Email email, String senhaHash, UUID grupoProprietarioId) {
		return new Usuario(nome, email, senhaHash, grupoProprietarioId, true);
	}

	public UUID getGrupoId() {
		return grupoId;
	}

	public String getNome() {
		return nome;
	}

	public Email getEmail() {
		return new Email(email);
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public boolean isProprietario() {
		return proprietario;
	}

	public boolean isAtivo() {
		return ativo;
	}

}
