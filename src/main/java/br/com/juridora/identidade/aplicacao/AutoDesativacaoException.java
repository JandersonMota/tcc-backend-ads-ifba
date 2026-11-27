package br.com.juridora.identidade.aplicacao;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/** Quem gerencia usuários não desativa a si mesmo: perderia o acesso no mesmo instante. */
public class AutoDesativacaoException extends RegraNegocioException {

	public AutoDesativacaoException() {
		super("auto-desativacao", "Você não pode desativar o seu próprio usuário.");
	}

}
