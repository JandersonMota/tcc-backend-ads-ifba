package br.com.juridora.identidade.configuracao;

import br.com.juridora.identidade.aplicacao.PropriedadesLogin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Beans e propriedades do módulo identidade. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesLogin.class)
class IdentidadeConfiguracao {

}
