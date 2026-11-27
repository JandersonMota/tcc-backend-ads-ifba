package br.com.juridora.compartilhado.infraestrutura.seguranca;

/**
 * Autoridades do Spring Security que não vêm do catálogo de permissões.
 * <p>
 * As permissões {@code RECURSO_ACAO} viram authorities com o próprio nome. As operações
 * exclusivas do proprietário (RN04: gerenciar grupos e permissões, remover usuários,
 * backup...) exigem {@link #PAPEL_PROPRIETARIO}, derivado da claim {@code proprietario}.
 * Com o prefixo {@code ROLE_}, nenhuma permissão se confunde com o papel, e dar todas as
 * permissões a um usuário não o torna proprietário (RN05).
 */
public final class Autoridades {

	/** Exigido com {@code @PreAuthorize("hasRole('PROPRIETARIO')")}. */
	public static final String PAPEL_PROPRIETARIO = "ROLE_PROPRIETARIO";

	private Autoridades() {
	}

}
