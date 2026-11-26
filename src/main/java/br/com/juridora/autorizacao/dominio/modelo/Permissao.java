package br.com.juridora.autorizacao.dominio.modelo;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import br.com.juridora.compartilhado.excecao.ValidacaoException;

/**
 * Catálogo de permissões do MVP, no formato {@code RECURSO_ACAO}
 * (Matriz de Permissões, §2). Gravadas pelo nome em {@code grupo_permissao.permissao}.
 */
public enum Permissao {

	CLIENTE_VISUALIZAR,
	CLIENTE_CRIAR,
	CLIENTE_EDITAR,
	CLIENTE_EXCLUIR,

	PROCESSO_VISUALIZAR,
	PROCESSO_CRIAR,
	PROCESSO_EDITAR,
	PROCESSO_EXCLUIR,

	ATIVIDADE_VISUALIZAR,
	ATIVIDADE_CRIAR,
	ATIVIDADE_EDITAR,
	ATIVIDADE_EXCLUIR,
	ATIVIDADE_DELEGAR,

	FINANCEIRO_VISUALIZAR,
	FINANCEIRO_CRIAR,
	FINANCEIRO_EDITAR,
	FINANCEIRO_EXCLUIR,
	FINANCEIRO_PAGAMENTO_REGISTRAR,

	USUARIO_VISUALIZAR,
	USUARIO_GERENCIAR,

	DASHBOARD_VISUALIZAR,

	AUDITORIA_VISUALIZAR;

	/**
	 * Permissão pelo código {@code RECURSO_ACAO}, como chega na API (HU09).
	 *
	 * @throws ValidacaoException se o código não estiver no catálogo
	 */
	public static Permissao doCodigo(String codigo) {
		for (Permissao permissao : values()) {
			if (permissao.name().equals(codigo)) {
				return permissao;
			}
		}
		throw new ValidacaoException("Permissão desconhecida: " + codigo + ".");
	}

	/** Conjunto de permissões a partir dos códigos; repetidos contam uma vez. */
	public static Set<Permissao> dosCodigos(Collection<String> codigos) {
		Set<Permissao> permissoes = EnumSet.noneOf(Permissao.class);
		for (String codigo : codigos) {
			permissoes.add(doCodigo(codigo));
		}
		return permissoes;
	}

	/** Códigos das permissões, na ordem do catálogo. */
	public static List<String> codigos(Collection<Permissao> permissoes) {
		return permissoes.stream().sorted().map(Permissao::name).toList();
	}

}
