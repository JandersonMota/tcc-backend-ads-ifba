package br.com.juridora.identidade.dominio;

import br.com.juridora.compartilhado.excecao.RegraNegocioException;

/**
 * O grupo Proprietário é reservado ao dono do escritório (HU10, RN03, RN04): o proprietário
 * não sai dele e nenhum outro usuário entra nele.
 */
public class GrupoReservadoAoProprietarioException extends RegraNegocioException {

	public GrupoReservadoAoProprietarioException(String mensagem) {
		super("grupo-reservado-ao-proprietario", mensagem);
	}

	public static GrupoReservadoAoProprietarioException proprietarioNaoSai() {
		return new GrupoReservadoAoProprietarioException("O proprietário permanece no grupo Proprietário.");
	}

	public static GrupoReservadoAoProprietarioException membroNaoEntra() {
		return new GrupoReservadoAoProprietarioException(
				"O grupo Proprietário é reservado ao dono do escritório; escolha outro grupo.");
	}

}
