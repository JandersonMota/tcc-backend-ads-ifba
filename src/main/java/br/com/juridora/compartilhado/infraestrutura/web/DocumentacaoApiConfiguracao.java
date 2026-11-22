package br.com.juridora.compartilhado.infraestrutura.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI com autenticação Bearer (ADR-011): no Swagger UI, "Authorize" recebe o token
 * do {@code POST /api/autenticacao/login}. Cadastro e login marcam
 * {@code @SecurityRequirements} vazio para aparecerem como públicos.
 */
@Configuration(proxyBeanMethods = false)
class DocumentacaoApiConfiguracao {

	static final String ESQUEMA_BEARER = "bearer";

	@Bean
	OpenAPI documentacaoApi() {
		return new OpenAPI()
				.info(new Info().title("Juridora API").version("v1"))
				.components(new Components().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
	}

}
