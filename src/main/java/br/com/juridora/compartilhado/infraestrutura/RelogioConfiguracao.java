package br.com.juridora.compartilhado.infraestrutura;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, injetado onde o tempo importa para a regra (validade do token,
 * bloqueio de login). Testes unitários passam um {@link Clock} fixo direto no construtor.
 */
@Configuration(proxyBeanMethods = false)
class RelogioConfiguracao {

	@Bean
	@ConditionalOnMissingBean
	Clock relogio() {
		return Clock.systemUTC();
	}

}
