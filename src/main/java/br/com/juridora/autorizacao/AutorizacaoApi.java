package br.com.juridora.autorizacao;

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

}
