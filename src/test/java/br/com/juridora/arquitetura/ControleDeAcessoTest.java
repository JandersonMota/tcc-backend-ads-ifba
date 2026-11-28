package br.com.juridora.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.com.juridora.autorizacao.dominio.modelo.Permissao;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HU11 – toda ação verifica as permissões do usuário. Sem Docker, como o {@link CamadasTest}.
 * <p>
 * Todo endpoint declara a sua regra com {@code @PreAuthorize}; esquecer a anotação reprova o
 * build, em vez de deixar a rota aberta a qualquer usuário autenticado. A expressão só pode
 * usar:
 * <ul>
 *   <li>{@code hasAuthority('X')} ou {@code hasAnyAuthority('X', ...)} com X do catálogo
 *       {@link Permissao} (um erro de digitação negaria a rota a todos, sem aviso);</li>
 *   <li>{@code hasRole('PROPRIETARIO')}, para as operações exclusivas do dono (RN04, RN05);</li>
 *   <li>{@code isAuthenticated()}, para rotas do próprio usuário (perfil, logout);</li>
 *   <li>{@code permitAll()}, para as rotas públicas da {@code SegurancaConfiguracao}.</li>
 * </ul>
 */
class ControleDeAcessoTest {

	private static final String RAIZ = "br.com.juridora";

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(RAIZ);

	private static final Pattern CHAMADA = Pattern.compile("(\\w+)\\(([^)]*)\\)");

	private static final Pattern RESTO_PERMITIDO = Pattern.compile("[\\s()!]*((and|or)[\\s()!]*)*");

	private static final Set<String> PAPEIS = Set.of("PROPRIETARIO");

	private static final Set<String> CATALOGO = Arrays.stream(Permissao.values()).map(Permissao::name)
			.collect(Collectors.toUnmodifiableSet());

	@Test
	void todoEndpointDeclaraARegraDeAcesso() {
		methods().that().areMetaAnnotatedWith(RequestMapping.class)
				.and().areDeclaredInClassesThat().areMetaAnnotatedWith(Controller.class)
				.should(declararRegraDeAcesso())
				.because("toda ação verifica as permissões do usuário (HU11); rota sem regra é um furo")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	@Test
	void regraDeAcessoUsaSoPermissoesDoCatalogoEOPapelDeProprietario() {
		methods().that().areMetaAnnotatedWith(PreAuthorize.class)
				.should(usarSoExpressoesConhecidas())
				.because("permissão fora do catálogo nega a rota a todos sem aviso (HU11)")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	private static ArchCondition<JavaMethod> declararRegraDeAcesso() {
		return new ArchCondition<>("declarar a regra de acesso com @PreAuthorize") {
			@Override
			public void check(JavaMethod metodo, ConditionEvents eventos) {
				if (regra(metodo).isEmpty()) {
					eventos.add(SimpleConditionEvent.violated(metodo,
							metodo.getFullName() + " não declara @PreAuthorize"));
				}
			}
		};
	}

	private static ArchCondition<JavaMethod> usarSoExpressoesConhecidas() {
		return new ArchCondition<>("usar só permissões do catálogo, o papel PROPRIETARIO, isAuthenticated ou permitAll") {
			@Override
			public void check(JavaMethod metodo, ConditionEvents eventos) {
				regra(metodo).flatMap(ControleDeAcessoTest::problema).ifPresent(problema -> eventos
						.add(SimpleConditionEvent.violated(metodo, metodo.getFullName() + ": " + problema)));
			}
		};
	}

	/** Regra do método ou, na falta dela, da classe. */
	private static Optional<String> regra(JavaMethod metodo) {
		return metodo.tryGetAnnotationOfType(PreAuthorize.class)
				.or(() -> metodo.getOwner().tryGetAnnotationOfType(PreAuthorize.class))
				.map(PreAuthorize::value);
	}

	/** Descrição do que está errado na expressão, ou vazio se ela é válida. */
	static Optional<String> problema(String expressao) {
		Matcher chamada = CHAMADA.matcher(expressao);
		boolean alguma = false;
		while (chamada.find()) {
			alguma = true;
			String funcao = chamada.group(1);
			Set<String> argumentos = Arrays.stream(chamada.group(2).split(","))
					.map(String::strip).filter(argumento -> !argumento.isEmpty())
					.map(argumento -> argumento.replaceAll("^'|'$", ""))
					.collect(Collectors.toSet());
			Optional<String> erro = switch (funcao) {
				case "permitAll", "isAuthenticated" -> argumentos.isEmpty() ? Optional.empty()
						: Optional.of(funcao + " não recebe argumentos");
				case "hasRole" -> argumentos.size() == 1 && PAPEIS.containsAll(argumentos) ? Optional.empty()
						: Optional.of("papel desconhecido em " + chamada.group());
				case "hasAuthority", "hasAnyAuthority" -> !argumentos.isEmpty() && CATALOGO.containsAll(argumentos)
						? Optional.empty() : Optional.of("permissão fora do catálogo em " + chamada.group());
				default -> Optional.of("função não permitida: " + funcao);
			};
			if (erro.isPresent()) {
				return erro;
			}
		}
		if (!alguma || !RESTO_PERMITIDO.matcher(CHAMADA.matcher(expressao).replaceAll("")).matches()) {
			return Optional.of("expressão não reconhecida: " + expressao);
		}
		return Optional.empty();
	}

	@Test
	void validadorDeExpressoesRecusaErrosComuns() {
		assertThat(problema("hasAuthority('USUARIO_GERENCIAR')")).isEmpty();
		assertThat(problema("hasAnyAuthority('USUARIO_GERENCIAR', 'CLIENTE_CRIAR')"))
				.isEmpty();
		assertThat(problema("hasRole('PROPRIETARIO') or hasAuthority('CLIENTE_CRIAR')"))
				.isEmpty();
		assertThat(problema("hasAuthority('USUARIO_GERENCIA')")).isPresent();
		assertThat(problema("hasRole('ADMIN')")).isPresent();
		assertThat(problema("hasAuthority('ROLE_PROPRIETARIO')")).isPresent();
		assertThat(problema("true")).isPresent();
		assertThat(problema("@bean.pode(#id)")).isPresent();
	}

}
