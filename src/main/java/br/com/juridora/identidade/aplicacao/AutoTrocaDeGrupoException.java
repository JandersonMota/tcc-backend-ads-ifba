package br.com.juridora.identidade.aplicacao;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/** Quem gerencia usuários não troca o próprio grupo: seria dar a si mesmo outras permissões (HU10). */
public class AutoTrocaDeGrupoException extends RegraNegocioException {

	public AutoTrocaDeGrupoException() {
		super("auto-troca-de-grupo", "Você não pode trocar o seu próprio grupo.");
	}

}
