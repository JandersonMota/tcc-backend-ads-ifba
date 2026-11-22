package br.com.juridora.identidade.adaptador.saida.seguranca;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import br.com.juridora.compartilhado.infraestrutura.seguranca.ClaimsToken;
import br.com.juridora.compartilhado.infraestrutura.seguranca.PropriedadesJwt;
import br.com.juridora.identidade.dominio.EmissorToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite o JWT assinado com RS256 (ADR-014). Claims: {@code iss}, {@code sub} (usuário),
 * {@code tenant_id}, {@code proprietario}, {@code permissoes}, {@code iat}, {@code exp}
 * e {@code jti} (identificador único, para a revogação no logout, HU07).
 */
@Component
class JwtEmissorToken implements EmissorToken {

	private final JwtEncoder encoder;

	private final PropriedadesJwt propriedades;

	private final Clock relogio;

	JwtEmissorToken(JwtEncoder encoder, PropriedadesJwt propriedades, Clock relogio) {
		this.encoder = encoder;
		this.propriedades = propriedades;
		this.relogio = relogio;
	}

	@Override
	public TokenAcesso emitir(UUID usuarioId, UUID tenantId, boolean proprietario, Set<String> permissoes) {
		Instant agora = relogio.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(propriedades.emissor())
				.subject(usuarioId.toString())
				.issuedAt(agora)
				.expiresAt(agora.plus(propriedades.validade()))
				.id(UUID.randomUUID().toString())
				.claim(ClaimsToken.TENANT_ID, tenantId.toString())
				.claim(ClaimsToken.PROPRIETARIO, proprietario)
				.claim(ClaimsToken.PERMISSOES, permissoes.stream().sorted().toList())
				.build();
		JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
		String valor = encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
		return new TokenAcesso(valor, propriedades.validade());
	}

}
