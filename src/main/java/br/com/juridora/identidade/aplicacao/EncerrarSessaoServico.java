package br.com.juridora.identidade.aplicacao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.identidade.dominio.TokenRevogadoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logout (HU07, RF03, ADR-016): revoga o token da requisição até ele expirar. Só o token
 * usado na chamada é encerrado; outros logins do mesmo usuário continuam valendo.
 * <p>
 * Usuário e tenant vêm do {@link TenantContext}, definido a partir do JWT validado.
 */
@Service
public class EncerrarSessaoServico {

	private static final Logger log = LoggerFactory.getLogger(EncerrarSessaoServico.class);

	private final TokenRevogadoRepositorio tokensRevogados;

	private final TenantContext tenantContext;

	EncerrarSessaoServico(TokenRevogadoRepositorio tokensRevogados, TenantContext tenantContext) {
		this.tokensRevogados = tokensRevogados;
		this.tenantContext = tenantContext;
	}

	/** Encerra a sessão do token {@code jti}, que vale até {@code expiraEm}. */
	public void encerrar(UUID jti, Instant expiraEm) {
		Objects.requireNonNull(jti, "jti é obrigatório");
		Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
		tokensRevogados.revogar(jti, tenantContext.usuarioAtual(), expiraEm);
	}

	/** Apaga as revogações de tokens que já expiraram (tarefa agendada, sem tenant). */
	public void removerRevogacoesVencidas() {
		int removidas = tokensRevogados.removerVencidos();
		if (removidas > 0) {
			log.debug("{} revogações de token vencidas removidas", removidas);
		}
	}

}
