package br.com.juridora.identidade.dominio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * <p>
 * {@code sessoesValidasDesde}: tokens emitidos antes desse instante deixam de valer (HU08).
 * É marcado ao desativar, para que uma reativação logo depois não traga de volta os tokens
 * antigos.
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

	@Column(name = "sessoes_validas_desde")
	private Instant sessoesValidasDesde;

	protected Usuario() {
	}

	private Usuario(String nome, Email email, String senhaHash, UUID grupoId, boolean proprietario) {
		this.nome = nomeValido(nome);
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

	/** Membro da equipe cadastrado pelo proprietário (HU08), já ativo e nunca proprietário (RN05). */
	public static Usuario membro(String nome, Email email, String senhaHash, UUID grupoId) {
		return new Usuario(nome, email, senhaHash, grupoId, false);
	}

	/** Edição do cadastro (HU08). O e-mail novo precisa continuar único na plataforma. */
	public void alterar(String nome, Email email) {
		this.nome = nomeValido(nome);
		this.email = Objects.requireNonNull(email, "email é obrigatório").valor();
	}

	/**
	 * Coloca o usuário em outro grupo (HU10). As permissões novas chegam no próximo login.
	 * Se o grupo pertence ao escritório e não é o Proprietário, quem confere é o caso de uso,
	 * pela API da autorizacao.
	 *
	 * @return {@code true} se o grupo mudou
	 * @throws GrupoReservadoAoProprietarioException se for o proprietário (ele não sai do grupo Proprietário)
	 */
	public boolean trocarGrupo(UUID novoGrupoId) {
		Objects.requireNonNull(novoGrupoId, "novoGrupoId é obrigatório");
		if (proprietario) {
			throw GrupoReservadoAoProprietarioException.proprietarioNaoSai();
		}
		if (novoGrupoId.equals(grupoId)) {
			return false;
		}
		grupoId = novoGrupoId;
		return true;
	}

	/**
	 * Desativa o usuário (HU08, RN08): ele não autentica mais e os tokens já emitidos param
	 * de valer. Os registros dele permanecem. Desativar quem já está desativado não muda nada.
	 *
	 * @throws ProprietarioNaoDesativavelException se for o proprietário (RN03: o escritório
	 *         sempre tem um proprietário ativo)
	 */
	public void desativar(Instant agora) {
		if (proprietario) {
			throw new ProprietarioNaoDesativavelException();
		}
		if (!ativo) {
			return;
		}
		ativo = false;
		// O iat do JWT tem precisão de segundos, então a marca também. Enquanto desativado, o
		// próprio "ativo" já recusa qualquer token; a marca só pesa depois de reativar.
		sessoesValidasDesde = Objects.requireNonNull(agora, "agora é obrigatório").truncatedTo(ChronoUnit.SECONDS);
	}

	/** Reativa o usuário (HU08). Os tokens emitidos antes da desativação continuam inválidos. */
	public void reativar() {
		ativo = true;
	}

	private static String nomeValido(String nome) {
		if (nome == null || nome.isBlank()) {
			throw new ValidacaoException("O nome é obrigatório.");
		}
		String limpo = nome.strip();
		if (limpo.length() > TAMANHO_MAXIMO_NOME) {
			throw new ValidacaoException("O nome deve ter até " + TAMANHO_MAXIMO_NOME + " caracteres.");
		}
		return limpo;
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

	public Instant getSessoesValidasDesde() {
		return sessoesValidasDesde;
	}

}
