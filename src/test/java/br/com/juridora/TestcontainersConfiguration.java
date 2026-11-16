package br.com.juridora;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL real para os testes, com os mesmos usuários do ambiente local
 * ({@code docker/postgres/01-criar-usuarios.sh}):
 * <ul>
 *   <li>Flyway conecta como {@code app_owner}, dono das tabelas e das políticas de RLS;</li>
 *   <li>a aplicação conecta como {@code app_user}, sem {@code SUPERUSER}/{@code BYPASSRLS},
 *       para que o RLS seja realmente aplicado (Plano de Testes, §5).</li>
 * </ul>
 * O superusuário do contêiner só é usado pelo script de inicialização.
 * <p>
 * As migrações de {@code db/migration-teste} (tabela usada só nos testes de isolamento)
 * são aplicadas depois das migrações reais.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final String SENHA_APP_OWNER = "app_owner";
	private static final String SENHA_APP_USER = "app_user";

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
				.withEnv("APP_OWNER_SENHA", SENHA_APP_OWNER)
				.withEnv("APP_USER_SENHA", SENHA_APP_USER)
				.withCopyFileToContainer(
						MountableFile.forHostPath("docker/postgres/01-criar-usuarios.sh"),
						"/docker-entrypoint-initdb.d/01-criar-usuarios.sh");
	}

	@Bean
	DynamicPropertyRegistrar propriedadesDoBanco(PostgreSQLContainer postgres) {
		return registro -> {
			registro.add("spring.datasource.url", postgres::getJdbcUrl);
			registro.add("spring.datasource.username", () -> "app_user");
			registro.add("spring.datasource.password", () -> SENHA_APP_USER);
			registro.add("spring.flyway.user", () -> "app_owner");
			registro.add("spring.flyway.password", () -> SENHA_APP_OWNER);
			registro.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/migration-teste");
		};
	}

}
