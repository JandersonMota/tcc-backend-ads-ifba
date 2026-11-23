package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.util.UUID;

/**
 * Porta consultada pelo {@link TenantDoTokenFiltro} em toda requisição autenticada:
 * diz se o token ({@code jti}) foi encerrado no logout (HU07, ADR-016).
 * <p>
 * Implementada pela {@code identidade}, dona da tabela {@code token_revogado}. Assim o
 * {@code compartilhado} não depende de nenhum módulo de negócio.
 */
public interface TokensRevogados {

	/**
	 * Chamado já dentro do tenant do token ({@code TenantContext#executarComo}), antes
	 * de abrir qualquer transação.
	 */
	boolean contem(UUID jti);

}
