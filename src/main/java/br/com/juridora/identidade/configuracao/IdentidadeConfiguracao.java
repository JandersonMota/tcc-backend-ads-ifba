package br.com.juridora.identidade.configuracao;

import br.com.juridora.identidade.aplicacao.PropriedadesLogin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Beans e propriedades do módulo identidade; liga o agendamento da limpeza de tokens (HU07). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesLogin.class)
@EnableScheduling
class IdentidadeConfiguracao {

}
