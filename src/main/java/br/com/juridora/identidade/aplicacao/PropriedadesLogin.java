package br.com.juridora.identidade.aplicacao;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bloqueio temporário de login (HU06, critério 3), em {@code juridora.seguranca.login}.
 *
 * @param maximoFalhas falhas seguidas, dentro da janela, que bloqueiam o e-mail
 * @param janela       período em que as falhas são somadas
 * @param bloqueio     por quanto tempo o e-mail fica bloqueado
 */
@ConfigurationProperties("juridora.seguranca.login")
public record PropriedadesLogin(
		@DefaultValue("5") int maximoFalhas,
		@DefaultValue("15m") Duration janela,
		@DefaultValue("15m") Duration bloqueio) {

	public PropriedadesLogin {
		if (maximoFalhas < 1) {
			throw new IllegalArgumentException("juridora.seguranca.login.maximo-falhas deve ser pelo menos 1");
		}
		Objects.requireNonNull(janela, "janela é obrigatória");
		Objects.requireNonNull(bloqueio, "bloqueio é obrigatório");
	}

}
