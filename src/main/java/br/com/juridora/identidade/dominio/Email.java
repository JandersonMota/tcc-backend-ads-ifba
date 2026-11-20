package br.com.juridora.identidade.dominio;

import java.util.Locale;
import java.util.regex.Pattern;

import br.com.juridora.compartilhado.excecao.ValidacaoException;

/**
 * E-mail de login: sem espaços nas pontas, em minúsculas e com formato válido.
 * Normalizado aqui para que {@code Joao@x.com} e {@code joao@x.com} sejam o mesmo login.
 */
public record Email(String valor) {

	public static final int TAMANHO_MAXIMO = 254;

	private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public Email {
		if (valor == null || valor.isBlank()) {
			throw new ValidacaoException("O e-mail é obrigatório.");
		}
		valor = valor.strip().toLowerCase(Locale.ROOT);
		if (valor.length() > TAMANHO_MAXIMO || !FORMATO.matcher(valor).matches()) {
			throw new ValidacaoException("O e-mail é inválido.");
		}
	}

}
