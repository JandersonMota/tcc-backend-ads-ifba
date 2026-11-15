package br.com.juridora.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Locale;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Regras de camadas da arquitetura hexagonal (ADR-008), complementares ao
 * {@link ModularidadeTest}: o Spring Modulith verifica as fronteiras <em>entre</em>
 * módulos; estas regras verificam as camadas <em>dentro</em> de cada módulo.
 * <p>
 * As regras usam {@code allowEmptyShould(true)} para passar enquanto os pacotes
 * ainda estão vazios; passam a valer automaticamente quando o código chegar.
 */
class CamadasTest {

	private static final String RAIZ = "br.com.juridora";

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(RAIZ);

	// ---------------------------------------------------------------- domínio

	@Test
	void dominioNaoDependeDeAplicacaoAdaptadorNemInfraestrutura() {
		noClasses().that().resideInAPackage(RAIZ + ".(*).dominio..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"..aplicacao..",
						"..adaptador..",
						"..configuracao..",
						RAIZ + ".compartilhado.infraestrutura..")
				.because("as dependências apontam para dentro: o domínio não conhece aplicação, adaptadores nem infraestrutura")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	@Test
	void dominioNaoDependeDeWebSegurancaNemClientesHttp() {
		noClasses().that().resideInAPackage(RAIZ + ".(*).dominio..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework.web..",
						"org.springframework.http..",
						"org.springframework.security..",
						"jakarta.servlet..",
						"java.net.http..",
						"org.springdoc..",
						"io.swagger..")
				.because("integrações externas ficam atrás de portas, em adaptadores de saída")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// -------------------------------------------------------------- aplicação

	@Test
	void aplicacaoNaoDependeDeAdaptadores() {
		noClasses().that().resideInAPackage(RAIZ + ".(*).aplicacao..")
				.should().dependOnClassesThat().resideInAPackage("..adaptador..")
				.because("casos de uso dependem de portas (interfaces), não das implementações")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// ------------------------------------------------------------ controllers

	@Test
	void controllersFicamApenasEmAdaptadorEntradaWeb() {
		classes().that().areMetaAnnotatedWith(Controller.class)
				.should().resideInAPackage(RAIZ + ".(*).adaptador.entrada.web..")
				.because("controllers são adaptadores de entrada dentro do módulo (ADR-008)")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	@Test
	void controllersTemSufixoController() {
		classes().that().areMetaAnnotatedWith(Controller.class)
				.should().haveSimpleNameEndingWith("Controller")
				.because("padrão de nomenclatura: Recurso + Controller")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	@Test
	void adaptadoresDeEntradaNaoAcessamRepositoriosNemAdaptadoresDeSaida() {
		noClasses().that().resideInAPackage(RAIZ + ".(*).adaptador.entrada..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"..dominio.repositorio..",
						"..adaptador.saida..",
						"org.springframework.data.repository..",
						"jakarta.persistence..")
				.because("o controller chama um caso de uso (porta de entrada), nunca um repositório")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// ----------------------------------------------------------------- tenant

	@Test
	void dtosDeEntradaNaoTemCampoDeTenant() {
		classes().that().resideInAPackage(RAIZ + ".(*).adaptador.entrada..")
				.and().haveSimpleNameEndingWith("Request")
				.should(naoTerCampoDeTenant())
				.because("o tenant_id vem somente do JWT, nunca do corpo da requisição")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	@Test
	void controllersNaoRecebemTenantPorParametroHeaderOuPath() {
		classes().that().areMetaAnnotatedWith(Controller.class)
				.should(naoReceberTenantDaRequisicao())
				.because("o tenant_id vem somente do JWT, nunca de parâmetro, header ou path")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// ------------------------------------------------------------------ geral

	@Test
	void semInjecaoPorCampo() {
		GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
				.because("padrão de código: injeção de dependência por construtor")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// -------------------------------------------------------------- condições

	private static boolean mencionaTenant(String texto) {
		return texto != null && texto.toLowerCase(Locale.ROOT).replace("_", "").contains("tenant");
	}

	private static ArchCondition<JavaClass> naoTerCampoDeTenant() {
		return new ArchCondition<>("não ter campo de tenant") {
			@Override
			public void check(JavaClass classe, ConditionEvents eventos) {
				classe.getFields().stream()
						.filter(campo -> mencionaTenant(campo.getName()))
						.forEach(campo -> eventos.add(SimpleConditionEvent.violated(campo,
								campo.getFullName() + " recebe o tenant da requisição")));
			}
		};
	}

	private static final Set<Class<?>> ANOTACOES_DE_REQUISICAO =
			Set.of(RequestParam.class, RequestHeader.class, PathVariable.class);

	private static ArchCondition<JavaClass> naoReceberTenantDaRequisicao() {
		return new ArchCondition<>("não receber tenant por parâmetro, header ou path") {
			@Override
			public void check(JavaClass classe, ConditionEvents eventos) {
				classe.getMethods().forEach(metodo -> metodo.getParameters().forEach(parametro -> {
					for (JavaAnnotation<?> anotacao : parametro.getAnnotations()) {
						boolean deRequisicao = ANOTACOES_DE_REQUISICAO.stream()
								.anyMatch(tipo -> anotacao.getRawType().isEquivalentTo(tipo));
						if (deRequisicao && (mencionaTenant(valor(anotacao, "value")) || mencionaTenant(valor(anotacao, "name")))) {
							eventos.add(SimpleConditionEvent.violated(metodo,
									metodo.getFullName() + " recebe o tenant via @" + anotacao.getRawType().getSimpleName()));
						}
					}
				}));
			}
		};
	}

	private static String valor(JavaAnnotation<?> anotacao, String propriedade) {
		return anotacao.get(propriedade).map(Object::toString).orElse(null);
	}

}
