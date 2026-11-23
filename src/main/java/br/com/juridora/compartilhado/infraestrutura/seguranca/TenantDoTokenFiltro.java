package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.io.IOException;
import java.util.UUID;

import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adaptador de entrada do tenant nas requisições HTTP (ADR-012, HU06): roda depois da
 * validação do Bearer e executa o resto da requisição dentro de
 * {@link TenantContext#executarComo}, com o tenant da claim {@code tenant_id} e o usuário
 * de {@code sub} do JWT <strong>validado</strong>. É a única origem do tenant (RNF02).
 * <p>
 * Já no tenant do token, recusa o token encerrado no logout ({@link TokensRevogados},
 * HU07). A consulta roda sob RLS, por isso fica aqui e não no validador do JWT, que
 * ainda não conhece o tenant.
 * <p>
 * Requisição sem token (rotas públicas) segue sem tenant. Token válido sem
 * {@code tenant_id}, {@code sub} ou {@code jti} utilizáveis, ou já revogado, responde 401.
 */
class TenantDoTokenFiltro extends OncePerRequestFilter {

	private final TenantContext tenantContext;

	private final TokensRevogados tokensRevogados;

	private final RespostaErroSeguranca respostaErro;

	TenantDoTokenFiltro(TenantContext tenantContext, TokensRevogados tokensRevogados,
			RespostaErroSeguranca respostaErro) {
		this.tenantContext = tenantContext;
		this.tokensRevogados = tokensRevogados;
		this.respostaErro = respostaErro;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (!(autenticacao instanceof JwtAuthenticationToken token)) {
			cadeia.doFilter(requisicao, resposta);
			return;
		}
		UUID tenantId;
		UUID usuarioId;
		UUID jti;
		try {
			Jwt jwt = token.getToken();
			tenantId = UUID.fromString(jwt.getClaimAsString(ClaimsToken.TENANT_ID));
			usuarioId = UUID.fromString(jwt.getSubject());
			jti = UUID.fromString(jwt.getId());
		}
		catch (IllegalArgumentException | NullPointerException excecao) {
			recusar(requisicao, resposta, "Token sem tenant, usuário ou identificador");
			return;
		}
		try {
			tenantContext.executarComo(tenantId, usuarioId, () -> {
				if (tokensRevogados.contem(jti)) {
					recusarDentroDoTenant(requisicao, resposta);
				}
				else {
					continuar(cadeia, requisicao, resposta);
				}
			});
		}
		catch (FalhaNaCadeia falha) {
			if (falha.getCause() instanceof ServletException servlet) {
				throw servlet;
			}
			throw (IOException) falha.getCause();
		}
	}

	private void recusar(HttpServletRequest requisicao, HttpServletResponse resposta, String motivo)
			throws IOException {
		SecurityContextHolder.clearContext();
		respostaErro.commence(requisicao, resposta, new InvalidBearerTokenException(motivo));
	}

	private void recusarDentroDoTenant(HttpServletRequest requisicao, HttpServletResponse resposta) {
		try {
			recusar(requisicao, resposta, "Token revogado");
		}
		catch (IOException excecao) {
			throw new FalhaNaCadeia(excecao);
		}
	}

	private static void continuar(FilterChain cadeia, HttpServletRequest requisicao, HttpServletResponse resposta) {
		try {
			cadeia.doFilter(requisicao, resposta);
		}
		catch (IOException | ServletException excecao) {
			throw new FalhaNaCadeia(excecao);
		}
	}

	/** Leva as exceções verificadas da cadeia para fora do {@code ScopedValue}. */
	private static final class FalhaNaCadeia extends RuntimeException {

		FalhaNaCadeia(Exception causa) {
			super(causa);
		}

	}

}
