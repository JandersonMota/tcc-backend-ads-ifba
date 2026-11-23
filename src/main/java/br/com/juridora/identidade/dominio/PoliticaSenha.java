package br.com.juridora.identidade.dominio;

import java.nio.charset.StandardCharsets;

import br.com.juridora.compartilhado.excecao.ValidacaoException;

/**
 * Regras de senha (Política de Segurança §2.1), usadas no cadastro do escritório (HU05),
 * no login (HU06) e no cadastro de usuários (HU08).
 */
public final class PoliticaSenha {

	public static final int TAMANHO_MINIMO = 8;

	/** O BCrypt considera só os primeiros 72 bytes; acima disso a senha é rejeitada. */
	public static final int TAMANHO_MAXIMO = 72;

	private PoliticaSenha() {
	}

	/** @throws ValidacaoException se a senha for curta demais ou passar de 72 bytes */
	public static void validar(String senha) {
		if (senha == null || senha.length() < TAMANHO_MINIMO) {
			throw new ValidacaoException("A senha deve ter pelo menos " + TAMANHO_MINIMO + " caracteres.");
		}
		if (excedeTamanhoMaximo(senha)) {
			throw new ValidacaoException("A senha deve ter até " + TAMANHO_MAXIMO + " bytes.");
		}
	}

	/** {@code true} se a senha passa do limite do BCrypt (72 bytes em UTF-8). */
	public static boolean excedeTamanhoMaximo(String senha) {
		return senha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO;
	}

}
