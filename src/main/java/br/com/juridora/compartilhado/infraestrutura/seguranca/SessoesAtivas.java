package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta consultada pelo {@link TenantDoTokenFiltro} em toda requisição autenticada: diz
 * se a sessão do token ainda vale. Ela deixa de valer quando o token foi encerrado no
 * logout (HU07, ADR-016) ou quando o usuário foi desativado depois de recebê-lo (HU08).
 * <p>
 * Implementada pela {@code identidade}, dona das tabelas {@code usuario} e
 * {@code token_revogado}. Assim o {@code compartilhado} não depende de nenhum módulo de
 * negócio.
 */
public interface SessoesAtivas {

	/**
	 * Chamado já dentro do tenant do token ({@code TenantContext#executarComo}), antes
	 * de abrir qualquer transação.
	 *
	 * @param usuarioId claim {@code sub}
	 * @param jti claim {@code jti}
	 * @param emitidoEm claim {@code iat}
	 * @return {@code false} se o token foi revogado ou se o usuário não existe, está
	 *         desativado ou teve as sessões encerradas depois de {@code emitidoEm}
	 */
	boolean ativa(UUID usuarioId, UUID jti, Instant emitidoEm);

}
