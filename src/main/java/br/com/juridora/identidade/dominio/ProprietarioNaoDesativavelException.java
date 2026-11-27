package br.com.juridora.identidade.dominio;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/** RN03: o escritório sempre tem um proprietário ativo, então ele não pode ser desativado. */
public class ProprietarioNaoDesativavelException extends RegraNegocioException {

	public ProprietarioNaoDesativavelException() {
		super("proprietario-nao-desativavel", "O proprietário do escritório não pode ser desativado.");
	}

}
