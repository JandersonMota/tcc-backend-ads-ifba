package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import br.com.juridora.identidade.dominio.CredencialLogin;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.EmailJaCadastradoException;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementação JPA da porta {@link UsuarioRepositorio}.
 * <p>
 * Grava com {@code flush} para que a violação de {@code uk_usuario_email} apareça aqui
 * e vire {@link EmailJaCadastradoException}. A restrição única vale para a tabela
 * inteira, mesmo com RLS: detecta e-mail de outro escritório sem consulta prévia e
 * sem condição de corrida.
 * <p>
 * A credencial de login vem da função {@code credencial_por_email} (V5, ADR-015), via
 * JDBC: é a única leitura de {@code usuario} fora do tenant da execução.
 * <p>
 * A contagem de ativos usa uma trava consultiva da transação
 * ({@code pg_advisory_xact_lock}) com a chave do tenant da transação: só serializa quem
 * conta usuários do mesmo escritório e é liberada sozinha no commit ou rollback.
 */
@Repository
class JpaUsuarioRepositorio implements UsuarioRepositorio {

	static final String RESTRICAO_EMAIL_UNICO = "uk_usuario_email";

	private static final String CREDENCIAL_POR_EMAIL =
			"select id, tenant_id, senha_hash, ativo from credencial_por_email(:email)";

	/** Classe da trava (1 = limite de usuários) e chave derivada do tenant da transação. */
	private static final String TRAVAR_LIMITE_USUARIOS =
			"select pg_advisory_xact_lock(1, hashtext(current_setting('app.tenant_id')))";

	private final SpringUsuarioRepositorio spring;

	private final JdbcClient jdbc;

	JpaUsuarioRepositorio(SpringUsuarioRepositorio spring, JdbcClient jdbc) {
		this.spring = spring;
		this.jdbc = jdbc;
	}

	@Override
	public Usuario adicionar(Usuario usuario) {
		return gravar(usuario);
	}

	@Override
	public Usuario atualizar(Usuario usuario) {
		return gravar(usuario);
	}

	@Override
	public Optional<Usuario> buscar(UUID id) {
		return spring.findById(id);
	}

	@Override
	public Page<Usuario> listar(Pageable pagina) {
		return spring.findAll(pagina);
	}

	@Override
	public long contarAtivosComTrava() {
		jdbc.sql(TRAVAR_LIMITE_USUARIOS).query().listOfRows();
		// Comando separado: em READ COMMITTED ele enxerga o que foi confirmado enquanto esperava a trava.
		return spring.countByAtivoTrue();
	}

	@Override
	public Optional<CredencialLogin> credencialPorEmail(Email email) {
		return jdbc.sql(CREDENCIAL_POR_EMAIL)
				.param("email", email.valor())
				.query((linha, numero) -> new CredencialLogin(
						linha.getObject("id", UUID.class),
						linha.getObject("tenant_id", UUID.class),
						linha.getString("senha_hash"),
						linha.getBoolean("ativo")))
				.optional();
	}

	private Usuario gravar(Usuario usuario) {
		try {
			return spring.saveAndFlush(usuario);
		}
		catch (DataIntegrityViolationException excecao) {
			if (violouEmailUnico(excecao)) {
				throw new EmailJaCadastradoException();
			}
			throw excecao;
		}
	}

	private static boolean violouEmailUnico(Throwable excecao) {
		for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacao && violacao.getConstraintName() != null
					&& violacao.getConstraintName().toLowerCase(Locale.ROOT).contains(RESTRICAO_EMAIL_UNICO)) {
				return true;
			}
		}
		return false;
	}

}
