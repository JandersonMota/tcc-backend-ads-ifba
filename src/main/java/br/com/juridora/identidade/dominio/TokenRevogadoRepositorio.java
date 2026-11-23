package br.com.juridora.identidade.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Tokens encerrados no logout (HU07, ADR-016). Cada registro só precisa durar até o
 * token expirar; depois disso o próprio {@code exp} já recusa o token.
 */
public interface TokenRevogadoRepositorio {

	/** Revoga o token no tenant da execução. Revogar duas vezes não é erro. */
	void revogar(UUID jti, UUID usuarioId, Instant expiraEm);

	/** Se o token foi revogado, no tenant da execução. */
	boolean contem(UUID jti);

	/** Remove os registros vencidos de todos os escritórios e devolve quantos removeu. */
	int removerVencidos();

}
