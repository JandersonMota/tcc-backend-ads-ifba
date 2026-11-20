package br.com.juridora.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import br.com.juridora.JuridoraApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica as fronteiras entre os módulos (Spring Modulith): sem ciclos e
 * sem acesso a pacotes internos de outro módulo.
 */
class ModularidadeTest {

	private static final ApplicationModules MODULOS = ApplicationModules.of(JuridoraApiApplication.class);

	/** Os 12 módulos previstos: o shared kernel e os módulos de negócio (HU01). */
	private static final List<String> MODULOS_PREVISTOS = List.of(
			"compartilhado", "identidade", "autorizacao", "escritorio", "cliente", "processo",
			"atividade", "documento", "financeiro", "notificacao", "auditoria", "portabilidade");

	@Test
	void deveRespeitarFronteirasEntreModulos() {
		MODULOS.verify();
	}

	/**
	 * Todos os módulos previstos são detectados, e nenhum outro: um pacote criado por
	 * engano direto em {@code br.com.juridora} viraria um módulo novo sem ninguém perceber.
	 */
	@Test
	void deveDetectarOsDozeModulosPrevistos() {
		assertThat(MODULOS.stream().map(modulo -> modulo.getIdentifier().toString()))
				.containsExactlyInAnyOrderElementsOf(MODULOS_PREVISTOS);
	}

	/**
	 * {@code compartilhado} precisa continuar aberto ({@code Type.OPEN}) também no
	 * classpath de teste. Um {@code package-info.java} do mesmo pacote em
	 * {@code src/test} sombreia o de {@code src/main} e apaga a anotação: os módulos
	 * de negócio passariam a violar a fronteira ao usar {@code EntidadeTenant},
	 * {@code TenantContext} ou {@code EventoDominio}.
	 */
	@Test
	void deveManterCompartilhadoComoModuloAberto() {
		assertThat(MODULOS.getModuleByName("compartilhado"))
				.hasValueSatisfying(modulo -> assertThat(modulo.isOpen()).isTrue());
	}

	@Test
	void deveGerarDocumentacaoDosModulos() {
		new Documenter(MODULOS)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml()
				.writeModuleCanvases();
	}

}
