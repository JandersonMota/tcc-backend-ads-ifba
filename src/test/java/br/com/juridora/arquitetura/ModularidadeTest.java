package br.com.juridora.arquitetura;

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

	@Test
	void deveRespeitarFronteirasEntreModulos() {
		MODULOS.verify();
	}

	@Test
	void deveGerarDocumentacaoDosModulos() {
		new Documenter(MODULOS)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml()
				.writeModuleCanvases();
	}

}
