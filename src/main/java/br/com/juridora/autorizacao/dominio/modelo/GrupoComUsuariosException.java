package br.com.juridora.autorizacao.dominio.modelo;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/** Grupo com usuários, ativos ou desativados, não pode ser excluído (RN07). */
public class GrupoComUsuariosException extends RegraNegocioException {

	public GrupoComUsuariosException() {
		super("grupo-com-usuarios", "O grupo tem usuários. Mova-os para outro grupo antes de excluí-lo.");
	}

}
