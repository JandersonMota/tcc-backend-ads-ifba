package br.com.juridora.compartilhado.infraestrutura.seguranca;

/**
 * Nomes das claims próprias do JWT (ADR-014), usados por quem emite ({@code identidade})
 * e por quem lê ({@link TenantDoTokenFiltro}, conversor de permissões).
 * <p>
 * {@code sub} é o ID do usuário; {@code iss}, {@code iat}, {@code exp} e {@code jti} são
 * as claims padrão.
 */
public final class ClaimsToken {

	/** Escritório do usuário: a única origem do tenant numa requisição (RNF02). */
	public static final String TENANT_ID = "tenant_id";

	/** Permissões {@code RECURSO_ACAO} do grupo do usuário, convertidas em authorities. */
	public static final String PERMISSOES = "permissoes";

	/** Se o usuário é o Proprietário do escritório (operações exclusivas, RN04). */
	public static final String PROPRIETARIO = "proprietario";

	private ClaimsToken() {
	}

}
