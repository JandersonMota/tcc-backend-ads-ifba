package br.com.juridora.autorizacao;

import java.util.Set;
import java.util.UUID;

/**
 * API pública do módulo autorizacao.
 * <p>
 * Regra de dependência: {@code autorizacao} nunca depende de {@code identidade}
 * (a identidade lê permissões daqui no login, HU06; o inverso criaria um ciclo).
 */
public interface AutorizacaoApi {

	/**
	 * Cria os grupos padrão (Proprietário, Gerente, Advogado, Estagiário e Usuário, RF05)
	 * no escritório da execução atual e devolve o ID do grupo Proprietário.
	 * O tenant vem do {@code TenantContext}, nunca de parâmetro. Participa da transação de quem chama.
	 */
	UUID criarGruposPadrao();

	/**
	 * Permissões ({@code RECURSO_ACAO}) do grupo, no escritório da execução atual.
	 * Grupo inexistente ou de outro escritório devolve conjunto vazio (o RLS não o encontra).
	 */
	Set<String> permissoesDoGrupo(UUID grupoId);

	/**
	 * {@code true} se o grupo existe no escritório da execução atual (HU08). Grupo de
	 * outro escritório devolve {@code false}.
	 * <p>
	 * Trava o grupo para leitura até o fim da transação de quem chama (HU09): enquanto um
	 * usuário é colocado nele, o grupo não pode ser excluído. Chame dentro da transação que
	 * grava o usuário.
	 */
	boolean grupoExiste(UUID grupoId);

}
