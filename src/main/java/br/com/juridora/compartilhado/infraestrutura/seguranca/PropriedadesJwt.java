package br.com.juridora.compartilhado.infraestrutura.seguranca;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do JWT (ADR-014), em {@code juridora.seguranca.jwt}.
 *
 * @param emissor      valor de {@code iss}, conferido na validação
 * @param validade     tempo de vida do token (curto: não há refresh token no MVP)
 * @param chavePrivada chave RSA privada em PEM (PKCS#8); só assina
 * @param chavePublica chave RSA pública em PEM (X.509); só valida
 */
@ConfigurationProperties("juridora.seguranca.jwt")
public record PropriedadesJwt(
		@DefaultValue("juridora") String emissor,
		@DefaultValue("15m") Duration validade,
		String chavePrivada,
		String chavePublica) {

	boolean temChaves() {
		return chavePrivada != null && !chavePrivada.isBlank() && chavePublica != null && !chavePublica.isBlank();
	}

	boolean temChaveIncompleta() {
		boolean privada = chavePrivada != null && !chavePrivada.isBlank();
		boolean publica = chavePublica != null && !chavePublica.isBlank();
		return privada != publica;
	}

}
