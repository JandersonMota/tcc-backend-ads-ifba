package br.com.juridora.autorizacao.dominio.modelo;

import static br.com.juridora.autorizacao.dominio.modelo.Permissao.ATIVIDADE_CRIAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.ATIVIDADE_DELEGAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.ATIVIDADE_EDITAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.ATIVIDADE_EXCLUIR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.ATIVIDADE_VISUALIZAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.CLIENTE_CRIAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.CLIENTE_EDITAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.CLIENTE_EXCLUIR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.CLIENTE_VISUALIZAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.DASHBOARD_VISUALIZAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.FINANCEIRO_CRIAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.FINANCEIRO_EDITAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.FINANCEIRO_PAGAMENTO_REGISTRAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.FINANCEIRO_VISUALIZAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.PROCESSO_CRIAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.PROCESSO_EDITAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.PROCESSO_EXCLUIR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.PROCESSO_VISUALIZAR;
import static br.com.juridora.autorizacao.dominio.modelo.Permissao.USUARIO_VISUALIZAR;

import java.util.EnumSet;
import java.util.Set;

/**
 * Grupos criados para todo escritório novo (RF05), com as permissões padrão da
 * Matriz de Permissões, §4. O proprietário pode alterá-las depois.
 */
public enum GrupoPadrao {

	PROPRIETARIO("Proprietário", EnumSet.allOf(Permissao.class)),

	GERENTE("Gerente", EnumSet.of(
			CLIENTE_VISUALIZAR, CLIENTE_CRIAR, CLIENTE_EDITAR, CLIENTE_EXCLUIR,
			PROCESSO_VISUALIZAR, PROCESSO_CRIAR, PROCESSO_EDITAR, PROCESSO_EXCLUIR,
			ATIVIDADE_VISUALIZAR, ATIVIDADE_CRIAR, ATIVIDADE_EDITAR, ATIVIDADE_EXCLUIR, ATIVIDADE_DELEGAR,
			FINANCEIRO_VISUALIZAR, FINANCEIRO_CRIAR, FINANCEIRO_EDITAR, FINANCEIRO_PAGAMENTO_REGISTRAR,
			USUARIO_VISUALIZAR, DASHBOARD_VISUALIZAR)),

	ADVOGADO("Advogado", EnumSet.of(
			CLIENTE_VISUALIZAR, CLIENTE_CRIAR, CLIENTE_EDITAR,
			PROCESSO_VISUALIZAR, PROCESSO_CRIAR, PROCESSO_EDITAR,
			ATIVIDADE_VISUALIZAR, ATIVIDADE_CRIAR, ATIVIDADE_EDITAR, ATIVIDADE_DELEGAR,
			USUARIO_VISUALIZAR, DASHBOARD_VISUALIZAR)),

	ESTAGIARIO("Estagiário", EnumSet.of(
			CLIENTE_VISUALIZAR,
			PROCESSO_VISUALIZAR, PROCESSO_EDITAR,
			ATIVIDADE_VISUALIZAR, ATIVIDADE_CRIAR, ATIVIDADE_EDITAR,
			USUARIO_VISUALIZAR, DASHBOARD_VISUALIZAR)),

	USUARIO("Usuário", EnumSet.of(
			CLIENTE_VISUALIZAR,
			PROCESSO_VISUALIZAR,
			ATIVIDADE_VISUALIZAR, ATIVIDADE_EDITAR,
			USUARIO_VISUALIZAR, DASHBOARD_VISUALIZAR));

	private final String nome;
	private final Set<Permissao> permissoes;

	GrupoPadrao(String nome, Set<Permissao> permissoes) {
		this.nome = nome;
		this.permissoes = Set.copyOf(permissoes);
	}

	public String nome() {
		return nome;
	}

	public Set<Permissao> permissoes() {
		return permissoes;
	}

}
