package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.util.ArrayList;
import java.util.List;

import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security da API (HU06, ADR-014): JWT Bearer, sem sessão e negando por padrão
 * (Política de Segurança §2.2: endpoint sem regra explícita é bloqueado).
 * <p>
 * Sem CSRF: a API não usa cookie, só o cabeçalho {@code Authorization}. As permissões
 * da claim {@code permissoes} viram authorities, para o {@code @PreAuthorize} da HU11.
 * O proprietário recebe também {@link Autoridades#PAPEL_PROPRIETARIO}, que protege as
 * operações exclusivas dele (RN04, HU09); nenhuma permissão atribuível o substitui (RN05).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SegurancaConfiguracao {

	/** Rotas públicas: cadastro e login (POST), documentação da API e health check. */
	private static final String[] ROTAS_PUBLICAS_POST = { "/api/autenticacao/cadastro", "/api/autenticacao/login" };

	private static final String[] ROTAS_PUBLICAS = { "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
			"/actuator/health", "/actuator/health/**", "/error" };

	@Bean
	SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, TenantContext tenantContext,
			SessoesAtivas sessoesAtivas) throws Exception {
		RespostaErroSeguranca respostaErro = new RespostaErroSeguranca();
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rotas -> rotas
						.requestMatchers(HttpMethod.POST, ROTAS_PUBLICAS_POST).permitAll()
						.requestMatchers(ROTAS_PUBLICAS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(servidor -> servidor
						.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDePermissoes()))
						.authenticationEntryPoint(respostaErro)
						.accessDeniedHandler(respostaErro))
				.exceptionHandling(erros -> erros
						.authenticationEntryPoint(respostaErro)
						.accessDeniedHandler(respostaErro))
				.addFilterAfter(new TenantDoTokenFiltro(tenantContext, sessoesAtivas, respostaErro),
						BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	private static JwtAuthenticationConverter conversorDePermissoes() {
		JwtGrantedAuthoritiesConverter permissoes = new JwtGrantedAuthoritiesConverter();
		permissoes.setAuthoritiesClaimName(ClaimsToken.PERMISSOES);
		permissoes.setAuthorityPrefix("");
		JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
		conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
			List<GrantedAuthority> autoridades = new ArrayList<>(permissoes.convert(jwt));
			if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(ClaimsToken.PROPRIETARIO))) {
				autoridades.add(new SimpleGrantedAuthority(Autoridades.PAPEL_PROPRIETARIO));
			}
			return autoridades;
		});
		return conversor;
	}

}
