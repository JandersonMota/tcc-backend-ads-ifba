package br.com.juridora.identidade.aplicacao;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/**
 * RN09: o plano do escritório já tem o máximo de usuários ativos. Bloqueia cadastrar e
 * reativar; editar e desativar continuam permitidos.
 */
public class LimiteDeUsuariosAtingidoException extends RegraNegocioException {

	public LimiteDeUsuariosAtingidoException(int limite) {
		super("limite-do-plano", "O plano do escritório permite até " + limite
				+ " usuários ativos. Desative um usuário para liberar espaço.");
	}

}
