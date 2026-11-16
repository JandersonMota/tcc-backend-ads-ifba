package br.com.juridora.compartilhado.infraestrutura.tenant;

/** Operação de negócio executada sem tenant definido (falha segura). */
public class TenantNaoDefinidoException extends IllegalStateException {

	public TenantNaoDefinidoException() {
		super("Nenhum tenant definido na execução atual");
	}

}
