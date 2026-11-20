package br.com.juridora.escritorio.dominio.modelo;

import br.com.juridora.compartilhado.excecao.ValidacaoException;

/** Nome do escritório: obrigatório, até 200 caracteres, sem espaços nas pontas. */
public record NomeEscritorio(String valor) {

	public static final int TAMANHO_MAXIMO = 200;

	public NomeEscritorio {
		if (valor == null || valor.isBlank()) {
			throw new ValidacaoException("O nome do escritório é obrigatório.");
		}
		valor = valor.strip();
		if (valor.length() > TAMANHO_MAXIMO) {
			throw new ValidacaoException("O nome do escritório deve ter até " + TAMANHO_MAXIMO + " caracteres.");
		}
	}

}
