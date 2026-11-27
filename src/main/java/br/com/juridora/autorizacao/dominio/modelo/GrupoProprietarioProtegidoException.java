package br.com.juridora.autorizacao.dominio.modelo;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/** Alteração proibida no grupo Proprietário (RN04): renomear, excluir ou mudar as permissões. */
public class GrupoProprietarioProtegidoException extends RegraNegocioException {

	public GrupoProprietarioProtegidoException(String mensagem) {
		super("grupo-proprietario-protegido", mensagem);
	}

}
