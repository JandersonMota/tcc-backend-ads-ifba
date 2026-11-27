package br.com.juridora.escritorio.configuracao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuração do módulo escritorio: limites dos planos (RN09). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesPlanos.class)
class EscritorioConfiguracao {
}
