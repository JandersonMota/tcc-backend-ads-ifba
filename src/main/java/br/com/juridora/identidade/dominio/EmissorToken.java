package br.com.juridora.identidade.dominio;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Porta de saída que emite o token de acesso do usuário autenticado (HU06, ADR-014). */
public interface EmissorToken {

	/** Emite um token com o escritório (tenant), o usuário e as permissões dele. */
	TokenAcesso emitir(UUID usuarioId, UUID tenantId, boolean proprietario, Set<String> permissoes);

	/** Token emitido e por quanto tempo ele vale. */
	record TokenAcesso(String valor, Duration validade) {

		public TokenAcesso {
			Objects.requireNonNull(valor, "valor é obrigatório");
			Objects.requireNonNull(validade, "validade é obrigatória");
		}

		@Override
		public String toString() {
			// nunca expor o token em logs
			return "TokenAcesso[validade=" + validade + "]";
		}

	}

}
