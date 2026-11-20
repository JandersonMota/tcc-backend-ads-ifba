package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.util.Locale;

import br.com.juridora.identidade.dominio.EmailJaCadastradoException;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implementação JPA da porta {@link UsuarioRepositorio}.
 * <p>
 * Grava com {@code flush} para que a violação de {@code uk_usuario_email} apareça aqui
 * e vire {@link EmailJaCadastradoException}. A restrição única vale para a tabela
 * inteira, mesmo com RLS: detecta e-mail de outro escritório sem consulta prévia e
 * sem condição de corrida.
 */
@Repository
class JpaUsuarioRepositorio implements UsuarioRepositorio {

	static final String RESTRICAO_EMAIL_UNICO = "uk_usuario_email";

	private final SpringUsuarioRepositorio spring;

	JpaUsuarioRepositorio(SpringUsuarioRepositorio spring) {
		this.spring = spring;
	}

	@Override
	public Usuario adicionar(Usuario usuario) {
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
