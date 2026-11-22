package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Par de chaves RSA do JWT (ADR-014): a privada assina ({@link JwtEncoder}, usado pela
 * {@code identidade} no login) e a pública valida ({@link JwtDecoder}, usado pelo
 * Resource Server em toda requisição).
 * <p>
 * Sem chaves em {@code juridora.seguranca.jwt}, gera um par temporário a cada
 * inicialização (perfil {@code local} e testes): os tokens deixam de valer quando a
 * aplicação reinicia. Em produção as duas chaves devem vir de variável de ambiente.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesJwt.class)
class ChavesJwtConfiguracao {

	private static final Logger log = LoggerFactory.getLogger(ChavesJwtConfiguracao.class);

	private static final int TAMANHO_CHAVE_TEMPORARIA = 2048;

	private final RSAPublicKey chavePublica;

	private final RSAPrivateKey chavePrivada;

	ChavesJwtConfiguracao(PropriedadesJwt propriedades) {
		if (propriedades.temChaveIncompleta()) {
			throw new IllegalStateException(
					"Configure juridora.seguranca.jwt.chave-privada e chave-publica juntas, ou nenhuma das duas");
		}
		if (propriedades.temChaves()) {
			this.chavePrivada = RsaKeyConverters.pkcs8().convert(pem(propriedades.chavePrivada()));
			this.chavePublica = RsaKeyConverters.x509().convert(pem(propriedades.chavePublica()));
		}
		else {
			log.warn("Chaves do JWT não configuradas: usando um par temporário. "
					+ "Os tokens deixam de valer quando a aplicação reinicia (não use em produção).");
			KeyPair par = gerarParTemporario();
			this.chavePrivada = (RSAPrivateKey) par.getPrivate();
			this.chavePublica = (RSAPublicKey) par.getPublic();
		}
	}

	@Bean
	JwtEncoder jwtEncoder() {
		RSAKey chave = new RSAKey.Builder(chavePublica).privateKey(chavePrivada).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(chave)));
	}

	@Bean
	JwtDecoder jwtDecoder(PropriedadesJwt propriedades) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(chavePublica)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		// Assinatura, exp/nbf (com tolerância padrão de 60 s) e iss.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(propriedades.emissor()));
		return decoder;
	}

	private static ByteArrayInputStream pem(String conteudo) {
		return new ByteArrayInputStream(conteudo.strip().getBytes(StandardCharsets.UTF_8));
	}

	private static KeyPair gerarParTemporario() {
		try {
			KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
			gerador.initialize(TAMANHO_CHAVE_TEMPORARIA);
			return gerador.generateKeyPair();
		}
		catch (NoSuchAlgorithmException excecao) {
			throw new IllegalStateException("RSA indisponível na JVM", excecao);
		}
	}

}
