package br.com.juridora.identidade.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * O mínimo para conferir um login antes de saber o tenant (HU06, ADR-015): vem da função
 * {@code credencial_por_email}, a única leitura de {@code usuario} sem RLS.
 */
public record CredencialLogin(UUID usuarioId, UUID tenantId, String senhaHash, boolean ativo) {

	public CredencialLogin {
		Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
		Objects.requireNonNull(tenantId, "tenantId é obrigatório");
		Objects.requireNonNull(senhaHash, "senhaHash é obrigatório");
	}

	@Override
	public String toString() {
		// nunca expor o hash em logs
		return "CredencialLogin[usuarioId=" + usuarioId + ", tenantId=" + tenantId + ", ativo=" + ativo + "]";
	}

}
