# Testes e qualidade

Complementa `arquitetura-gestao-juridica/references/testes.md`. Aquele arquivo diz **o que** testar (tipos de teste, isolamento multi-tenant, regras ArchUnit, `TestcontainersConfiguration`); este diz **como** escrever bons testes. Todos os exemplos rodam com JUnit 6, AssertJ, Mockito e Testcontainers do projeto.

## Sumário

1. Princípios
2. Unitário: domínio e casos de uso
3. Fatia web com `MockMvcTester`
4. Integração com PostgreSQL
5. Eventos com `Scenario`
6. Dados de teste
7. Cobertura e revisão de testes
8. Aviso do Mockito no JDK 25

## 1. Princípios

- **Teste comportamento, não implementação.** O nome do método descreve a regra, em português e camelCase, sem prefixo `test`: `recusaNumeroInvalido`, `arquivarProcessoJaArquivadoResponde422` (como em `deveRespeitarFronteirasEntreModulos`).
- **Organize em preparação, ação e verificação,** com uma linha em branco entre os blocos.
- **Um comportamento por teste.** Vários `assert` são aceitáveis quando descrevem o mesmo resultado.
- **Pirâmide:** muitos testes unitários (sem Spring), alguns de fatia web, poucos de integração com Testcontainers, e os de arquitetura.
- **Teste que nunca falhou não prova nada.** Ao cobrir um bug, veja o teste falhar antes da correção.
- **Determinismo:**
  - `Clock.fixed` em vez do relógio real;
  - nada de `Thread.sleep` (use `Scenario` ou Awaitility);
  - nenhuma dependência de ordem entre testes;
  - nenhum dado compartilhado mutável.

## 2. Unitário: domínio e casos de uso

Regras com vários casos ficam em testes parametrizados:

```java
class NumeroProcessoTest {

	@ParameterizedTest
	@ValueSource(strings = {"0001234-28.2025.8.05.0001", "00012342820258050001", " 0001234-28.2025.8.05.0001 "})
	void aceitaNumeroValidoComOuSemMascara(String valor) {
		assertThat(NumeroProcesso.de(valor).formatado()).isEqualTo("0001234-28.2025.8.05.0001");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"0001234-29.2025.8.05.0001",   // dígito verificador errado
			"0001234-28.2025.8.05.000A",   // letra
			"123",                         // curto
			"000123428202580500011"})      // 21 dígitos
	void recusaNumeroInvalido(String valor) {
		assertThatThrownBy(() -> NumeroProcesso.de(valor))
				.isInstanceOf(NumeroProcessoInvalidoException.class)
				.hasMessageStartingWith("Número de processo inválido");
	}

}
```

Quando cada caso tem objetos diferentes, use `@MethodSource`:

```java
static Stream<Arguments> formasDeCobranca() {
	return Stream.of(
			arguments(new Honorario.Fixo(Dinheiro.de("3000")), Dinheiro.de("3000")),
			arguments(new Honorario.Exito(new BigDecimal("20")), Dinheiro.de("10000")),
			arguments(new Honorario.Misto(Dinheiro.de("1000"), new BigDecimal("10")), Dinheiro.de("6000")));
}

@ParameterizedTest
@MethodSource("formasDeCobranca")
void calculaValorDevidoConformeAFormaDeCobranca(Honorario honorario, Dinheiro esperado) {
	assertThat(honorario.valorDevido(Dinheiro.de("50000.00"))).isEqualTo(esperado);
}
```

Um teste também documenta uma regra de negócio, como a de arredondamento:

```java
@Test
void arredondaPercentualPeloMetodoBancario() {
	// 50% de 0,25 = 0,125 → HALF_EVEN vai para o par (0,12); HALF_UP daria 0,13
	assertThat(Dinheiro.de("0.25").percentual(new BigDecimal("50"))).isEqualTo(Dinheiro.de("0.12"));
}
```

Caso de uso, com as portas simuladas e o relógio fixo:

```java
@ExtendWith(MockitoExtension.class)
class ArquivarProcessoTest {

	private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

	@Mock
	private ProcessoRepository processos;

	@Mock
	private ApplicationEventPublisher eventos;

	@Test
	void arquivaSalvaEPublicaEventoComOInstanteDoRelogio() {
		var id = UUID.randomUUID();
		var processo = new Processo(NumeroProcesso.de("0001234-28.2025.8.05.0001"), UUID.randomUUID());
		given(processos.buscarPorId(id)).willReturn(Optional.of(processo));
		var arquivar = new ArquivarProcesso(processos, new TenantContext(), eventos, Clock.fixed(AGORA, ZoneOffset.UTC));

		arquivar.executar(id);

		assertThat(processo.getStatus()).isEqualTo(StatusProcesso.ARQUIVADO);
		then(processos).should().salvar(processo);
		var evento = ArgumentCaptor.forClass(ProcessoArquivadoEvent.class);
		then(eventos).should().publishEvent(evento.capture());
		assertThat(evento.getValue().ocorridoEm()).isEqualTo(AGORA);
	}

}
```

- **O teste fica no mesmo pacote do caso de uso.** Assim enxerga a classe package-private e a instancia com `new`, sem Spring.
- **Simule só portas** (interfaces que o projeto controla). Value objects, entidades e o `TenantContext` são baratos de construir de verdade.
- **Use o estilo BDD do Mockito** (`given`/`then`), que se lê como especificação.
- **Os IDs ficam nulos fora do banco.** O `EntidadeTenant` recebe o `id` do Hibernate; se a regra depender do ID, ela pertence a um teste de integração.

## 3. Fatia web com `MockMvcTester`

```java
@WebMvcTest(ProcessoController.class)   // org.springframework.boot.webmvc.test.autoconfigure
class ProcessoControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ConsultarProcessosUseCase consultarProcessos;

	@MockitoBean
	private ArquivarProcessoUseCase arquivarProcesso;

	@Test
	void processoInexistenteOuDeOutroEscritorioResponde404() {
		var id = UUID.randomUUID();
		given(consultarProcessos.buscar(id)).willThrow(new RecursoNaoEncontradoException("Processo não encontrado"));

		assertThat(mvc.get().uri("/api/processos/{id}", id))
				.hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson().extractingPath("$.title").isEqualTo("Recurso não encontrado");
	}

	@Test
	void arquivarProcessoJaArquivadoResponde422() {
		var id = UUID.randomUUID();
		willThrow(new RegraDeNegocioException("O processo já está arquivado")).given(arquivarProcesso).executar(id);

		assertThat(mvc.post().uri("/api/processos/{id}/arquivamento", id))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.bodyJson().extractingPath("$.detail").isEqualTo("O processo já está arquivado");
	}

}
```

- **O que a fatia carrega:** só a camada MVC (controllers, `@RestControllerAdvice`, conversores). O `TratadorGlobalDeErros` entra; serviços, repositórios e os `@Configuration` do scan ficam de fora. Por isso os casos de uso são `@MockitoBean`.
- **Teste o contrato:** status, campos do JSON (`bodyJson().extractingPath`) e o `ProblemDetail` de cada erro. A paginação (`Pageable`) também funciona na fatia.
- **Com o Spring Security (HU06),** cada endpoint ganha os cenários de 401 e 403 com o JWT de teste. Isso exige o `spring-boot-starter-security-test`, hoje comentado no `pom.xml`.

## 4. Integração com PostgreSQL

```java
@SpringBootTest
@EnableScenarios
@Import(TestcontainersConfiguration.class)
class ProcessoIT {

	private static final NumeroProcesso NUMERO = NumeroProcesso.de("0001234-28.2025.8.05.0001");

	@Autowired TenantContext tenantContext;
	@Autowired ProcessoRepository processos;
	@Autowired ArquivarProcessoUseCase arquivarProcesso;
	@Autowired TransactionTemplate transacao;
	@Autowired JdbcTemplate jdbc;

	UUID escritorio;
	UUID usuario;

	@BeforeEach
	void criarEscritorio() {
		// cada teste usa um escritório novo: o RLS isola os dados sem precisar limpar o banco
		escritorio = UUID.randomUUID();
		usuario = UUID.randomUUID();
		jdbc.update("INSERT INTO escritorio (id, nome) VALUES (?, ?)", escritorio, "Escritório de teste");
	}

	@Test
	void gravaNumeroPeloConversorEIncrementaVersaoAoAlterar() {
		var id = noEscritorio(() -> transacao.execute(status ->
				processos.salvar(new Processo(NUMERO, UUID.randomUUID())).getId()));

		noEscritorio(() -> transacao.execute(status -> {
			processos.buscarPorId(id).orElseThrow().arquivar();
			return null;
		}));

		var salvo = noEscritorio(() -> transacao.execute(status -> processos.buscarPorId(id).orElseThrow()));
		assertThat(salvo.getNumero()).isEqualTo(NUMERO);
		assertThat(salvo.getStatus()).isEqualTo(StatusProcesso.ARQUIVADO);
		assertThat(salvo.getVersao()).isEqualTo(1);
	}

	private <T> T noEscritorio(Supplier<T> acao) {
		return tenantContext.executarComo(escritorio, usuario, acao);
	}

}
```

- **Uso real:** o tenant é definido fora e a transação abre dentro, como acontece na aplicação.
- **Escritório novo por teste.** O RLS isola os dados de cada teste, sem `@DirtiesContext` nem limpeza manual. O `INSERT` em `escritorio` funciona sem tenant porque essa tabela não tem RLS.
- **Contexto Spring cacheado.** Cada combinação diferente de `@MockitoBean` e propriedades cria um novo contexto, com um novo contêiner (vários segundos). Mantenha a configuração dos `*IT` uniforme e evite mocks neles.
- **Sufixo `IT`,** para rodar no Failsafe (`./mvnw verify`), e Docker em execução.

## 5. Eventos com `Scenario`

```java
@Test
void arquivarPublicaEventoComTenantEUsuario(Scenario scenario) {
	var id = noEscritorio(() -> transacao.execute(status ->
			processos.salvar(new Processo(NUMERO, UUID.randomUUID())).getId()));

	// O Scenario executa o estímulo dentro de uma transação: o tenant precisa ser
	// definido ANTES, envolvendo o cenário inteiro (não dentro do stimulate).
	tenantContext.executarComo(escritorio, usuario, () -> scenario
			.stimulate(() -> arquivarProcesso.executar(id))
			.andWaitForEventOfType(ProcessoArquivadoEvent.class)
			.matching(evento -> evento.processoId().equals(id))
			.toArriveAndVerify(evento -> {
				assertThat(evento.tenantId()).isEqualTo(escritorio);
				assertThat(evento.executadoPor()).isEqualTo(usuario);
			}));
}
```

- **Como habilitar:** `@EnableScenarios` habilita o parâmetro `Scenario` em um `@SpringBootTest`; com `@ApplicationModuleTest`, ele já vem habilitado.
- **A armadilha:** com `executarComo` dentro de `stimulate`, o teste falha com "O tenant deve ser definido antes de abrir a transação". O exemplo acima evita isso.
- **Efeito do listener** (notificação gravada, auditoria): `andWaitForStateChange(() -> ...)` espera a mudança de estado, sem `sleep`.

## 6. Dados de teste

- **Fábricas com nomes de negócio** (`umProcessoAtivo()`, `umEscritorio()`), na própria classe de teste ou em uma classe de apoio no pacote de teste. Os valores padrão são válidos, e só o que importa para o teste fica explícito.
- **Valores válidos e fictícios:**
  - CNJ: `0001234-28.2025.8.05.0001`
  - CNPJ numérico: `11.222.333/0001-81`
  - CNPJ alfanumérico: `12.ABC.345/01DE-35`
  - e-mails em `exemplo.com.br`
- **Nunca use dados reais de clientes,** nem "só para testar" (LGPD e sigilo profissional).

## 7. Cobertura e revisão de testes

- **Relatório:** o JaCoCo gera `target/site/jacoco/index.html` no `./mvnw verify`.
- **Cobertura é sinal, não meta.** Domínio e casos de uso devem ter cobertura alta; DTOs e configuração, não. Cem por cento de cobertura com asserts fracos não protege nada.
- **Teste de mutação** (PIT) mede se os testes detectam mudanças no código e é um bom argumento de qualidade no TCC, mas é dependência nova: proponha antes.

Ao revisar testes, confira:

- [ ] O nome descreve o comportamento, e o teste falha por um único motivo.
- [ ] Os asserts são específicos (valor, status, mensagem), não só `isNotNull()`.
- [ ] Não há `Thread.sleep`, dependência de ordem, nem mock de value object ou entidade.
- [ ] A integração usa Testcontainers com PostgreSQL, nunca H2 (o RLS só existe no PostgreSQL).
- [ ] Cada endpoint novo tem os casos de autorização e de isolamento da skill de arquitetura.

## 8. Aviso do Mockito no JDK 25

O aviso aparece ao rodar os testes:

```
WARNING: A Java agent has been loaded dynamically (...byte-buddy-agent...)
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

O Mockito carrega o próprio agente em tempo de execução, e um JDK futuro vai bloquear isso. A correção, testada com este `pom.xml`, carrega o agente explicitamente. O `@{argLine}` preserva o agente do JaCoCo, que também usa `argLine`.

```xml
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-dependency-plugin</artifactId>
	<executions>
		<execution>
			<goals>
				<goal>properties</goal>
			</goals>
		</execution>
	</executions>
</plugin>
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-surefire-plugin</artifactId>
	<configuration>
		<argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>
	</configuration>
</plugin>
<!-- mesmo <configuration> no maven-failsafe-plugin -->
```

Hoje é só um aviso. Proponha a mudança ao usuário antes de alterar o `pom.xml`.
