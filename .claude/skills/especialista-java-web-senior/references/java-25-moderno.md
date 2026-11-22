# Java 25 moderno

## Sumário

1. O que mudou do 17 ao 25
2. Records e value objects
3. Hierarquias fechadas e pattern matching
4. Coleções e streams
5. Concorrência: threads virtuais e ScopedValue
6. Outros recursos úteis
7. Armadilhas

## 1. O que mudou do 17 ao 25

| Recurso | Final em | JEP | Uso neste projeto |
|---|---|---|---|
| Pattern matching em `switch` | 21 | 441 | Sim |
| Record patterns (desestruturação) | 21 | 440 | Sim |
| Sequenced Collections | 21 | 431 | Sim |
| Threads virtuais | 21 | 444 | Com critério (seção 5) |
| Variáveis e padrões sem nome (`_`) | 22 | 456 | Sim |
| API de funções e memória externas (FFM) | 22 | 454 | Sem caso de uso |
| Comentários de documentação em Markdown (`///`) | 23 | 467 | Não: o projeto usa `/** */` |
| Stream Gatherers | 24 | 485 | Quando simplificar |
| `synchronized` sem fixar a thread virtual | 24 | 491 | Automático |
| Scoped Values | 25 | 506 | Sim (`TenantContext`) |
| Instruções antes de `super(...)` | 25 | 513 | Sim |
| `import module` | 25 | 511 | Só em scripts |
| Arquivos-fonte compactos e `main` de instância | 25 | 512 | Só em scripts e experimentos |
| API de derivação de chaves (KDF) | 25 | 510 | Só com criptografia própria |
| Cabeçalhos de objeto compactos | 25 | 519 | Flag da JVM (`performance-e-operacao.md`) |
| Cache AOT: ergonomia e perfil de métodos | 25 | 514, 515 | Inicialização mais rápida (`performance-e-operacao.md`) |

Estes recursos continuam em *preview* ou *incubator* no 25 e ficam fora do projeto:

- Structured Concurrency (505)
- Stable Values (502)
- tipos primitivos em padrões (507)
- codificação PEM (470)
- Vector API (508)

String Templates, que estiveram em preview no 21 e no 22, foram retirados da linguagem.

## 2. Records e value objects

Um value object é imutável, tem igualdade por valor e se valida na construção. O `record` já entrega `equals`, `hashCode`, `toString` e acessores. Falta só a validação, que vai no construtor compacto: assim não existe instância inválida.

```java
package br.com.juridora.processo.dominio.modelo;

/**
 * Número único de processo (Resolução CNJ 65/2008): {@code NNNNNNN-DD.AAAA.J.TR.OOOO}.
 * <p>
 * Guarda só os 20 dígitos e confere o dígito verificador {@code DD} (módulo 97,
 * ISO 7064): um número digitado errado nunca entra no domínio.
 */
public record NumeroProcesso(String digitos) {

	private static final Pattern VINTE_DIGITOS = Pattern.compile("\\d{20}");
	private static final Pattern COM_MASCARA = Pattern.compile("\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}");

	public NumeroProcesso {
		if (digitos == null || !VINTE_DIGITOS.matcher(digitos).matches() || !digitoVerificadorConfere(digitos)) {
			throw new NumeroProcessoInvalidoException(digitos);
		}
	}

	/** Aceita o número com máscara ({@code 0001234-28.2025.8.05.0001}) ou só com os 20 dígitos. */
	public static NumeroProcesso de(String valor) {
		var texto = valor == null ? "" : valor.strip();
		return new NumeroProcesso(COM_MASCARA.matcher(texto).matches() ? texto.replaceAll("[-.]", "") : texto);
	}

	public String formatado() {
		return "%s-%s.%s.%s.%s.%s".formatted(digitos.substring(0, 7), digitos.substring(7, 9),
				digitos.substring(9, 13), digitos.substring(13, 14), digitos.substring(14, 16), digitos.substring(16));
	}

	/**
	 * Confere o DD na ordem do CNJ (NNNNNNN, depois AAAA J TR, depois OOOO DD), em
	 * partes, porque o número inteiro tem 20 dígitos e não cabe em um {@code long}.
	 */
	private static boolean digitoVerificadorConfere(String d) {
		long r1 = Long.parseLong(d.substring(0, 7)) % 97;
		long r2 = Long.parseLong(r1 + d.substring(9, 16)) % 97;
		long r3 = Long.parseLong(r2 + d.substring(16, 20) + d.substring(7, 9)) % 97;
		return r3 == 1;
	}

}
```

```java
public class NumeroProcessoInvalidoException extends RegraDeNegocioException {

	public NumeroProcessoInvalidoException(String valor) {
		// Java 25 (JEP 513): instruções antes de super(...), desde que não usem `this`
		var exibicao = valor == null || valor.isBlank() ? "(vazio)" : valor.strip();
		super("Número de processo inválido: " + exibicao);
	}

}
```

- **Fábrica `de(...)` e construtor canônico.** A fábrica normaliza a entrada do usuário (máscara, espaços). O construtor canônico recebe dados já normalizados, como os que vêm do banco.
- **Formato na borda, invariante no domínio.** O `*Request` valida o formato (400). O value object garante a invariante mesmo quando o valor chega por outro caminho, como uma importação ou outro módulo.
- **Records com coleção.** Use `List.copyOf(...)` no construtor compacto. A cópia é defensiva e imutável, e recusa `null`.
- **Persistência:** value object de um campo usa `AttributeConverter` com `@Converter(autoApply = true)` no adaptador de persistência (`spring-boot-4.md`, seção 5). Value object com vários campos usa record `@Embeddable` (suportado desde o Hibernate 6.2).

### Dinheiro

```java
/**
 * Valor em reais, sempre com 2 casas.
 * <p>
 * Normalizar a escala no construtor faz o {@code equals} do record funcionar:
 * {@code new BigDecimal("10.0").equals(new BigDecimal("10.00"))} é {@code false}.
 * A política de arredondamento é regra de negócio: confirme com o usuário.
 */
public record Dinheiro(BigDecimal valor) {

	// Constantes antes de ZERO: campos estáticos são inicializados na ordem do código,
	// e o construtor de ZERO já usa ARREDONDAMENTO.
	private static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_EVEN;
	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

	public Dinheiro {
		Objects.requireNonNull(valor, "valor é obrigatório");
		valor = valor.setScale(2, ARREDONDAMENTO);
	}

	/** Sempre a partir de texto: {@code new BigDecimal(0.1)} carrega o erro do {@code double}. */
	public static Dinheiro de(String valor) {
		return new Dinheiro(new BigDecimal(valor));
	}

	public Dinheiro somar(Dinheiro outro) {
		return new Dinheiro(valor.add(outro.valor));
	}

	/** {@code percentual} em pontos percentuais: 20 = 20%. */
	public Dinheiro percentual(BigDecimal percentual) {
		return new Dinheiro(valor.multiply(percentual).divide(CEM, 2, ARREDONDAMENTO));
	}

	public boolean maiorQue(Dinheiro outro) {
		return valor.compareTo(outro.valor) > 0;
	}

}
```

Se `ZERO` fosse declarado antes de `ARREDONDAMENTO`, a classe falharia ao carregar com `NullPointerException`. É um erro fácil de cometer e difícil de entender sem conhecer a ordem de inicialização.

## 3. Hierarquias fechadas e pattern matching

Use `sealed` quando as variantes carregam dados diferentes. Com um enum, cada variante teria os mesmos campos e a lógica se espalharia em `if`s.

```java
/**
 * Formas de cobrança de honorários. Hierarquia fechada: um {@code switch} sem
 * {@code default} deixa de compilar quando surgir uma forma nova, apontando cada
 * ponto que precisa tratá-la.
 */
public sealed interface Honorario {

	record Fixo(Dinheiro valor) implements Honorario {
	}

	/** Honorário de êxito: percentual do proveito econômico obtido. */
	record Exito(BigDecimal percentual) implements Honorario {
	}

	record Misto(Dinheiro fixo, BigDecimal percentual) implements Honorario {
	}

	default Dinheiro valorDevido(Dinheiro proveitoEconomico) {
		return switch (this) {
			case Fixo(var valor) -> valor;
			case Exito(var percentual) -> proveitoEconomico.percentual(percentual);
			case Misto(var fixo, var percentual) -> fixo.somar(proveitoEconomico.percentual(percentual));
		};
	}

}
```

- **Sem `permits`.** Quando os subtipos estão no mesmo arquivo, o compilador os infere.
- **Sem `default` no `switch`.** É a exaustividade que transforma "esqueci um caso" em erro de compilação.
- **Guardas com `when`:** `case Exito(var p) when p.signum() == 0 -> Dinheiro.ZERO;`.
- **Cadeias de `instanceof`** viram um `switch` com padrões.

## 4. Coleções e streams

- **`List.of`, `Set.of` e `Map.of`** são imutáveis e recusam `null`.
- **`Stream.toList()`** devolve uma lista imutável. `Collectors.toList()` não garante nem mutabilidade nem imutabilidade.
- **Sequenced Collections:** `movimentacoes.getLast()`, `prazos.reversed()`, `mapaOrdenado.firstEntry()`.
- **Gatherers** para lotes e janelas:

```java
// exportação em lotes de 500 (ex.: portabilidade)
ids.stream()
		.gather(Gatherers.windowFixed(500))
		.forEach(this::exportarLote);
```

- **Streams são para transformação.** Um `for` é melhor quando há retorno antecipado ou exceção checada. Não faça efeitos colaterais em `map` ou `filter`.

## 5. Concorrência: threads virtuais e ScopedValue

### Threads virtuais

- `spring.threads.virtual.enabled: true` faz o Tomcat, o `@Async` e os agendamentos usarem threads virtuais.
- Ajudam com E/S bloqueante concorrente; não aceleram código limitado por CPU.
- O pool do banco (HikariCP) continua sendo o limite: mais threads que conexões só formam fila (`performance-e-operacao.md`).
- Desde o Java 24, `synchronized` não fixa mais a thread virtual na thread da plataforma. A fixação ainda ocorre em chamadas nativas; para diagnosticar, use o evento JFR `jdk.VirtualThreadPinned`.
- Nunca crie um pool de threads virtuais: use uma por tarefa (`Executors.newVirtualThreadPerTaskExecutor()`).

### ScopedValue

O valor é imutável e vale só dentro de `run` ou `call`. Ele **não** é herdado por `@Async`, `CompletableFuture`, executores ou `parallelStream`; só subtarefas de `StructuredTaskScope` (preview) o herdam. Neste projeto, todo salto assíncrono precisa redefinir o tenant com `TenantContext.executarComo`, como fazem os listeners de eventos.

`TenantContext.executarComo` aceita `Runnable` e `Supplier`. Para propagar exceções checadas (por exemplo, `chain.doFilter` em um filtro HTTP), o JDK 25 oferece `ScopedValue.Carrier.call(ScopedValue.CallableOp<T, X>) throws X`. Uma sobrecarga com `CallableOp` no `TenantContext` é a extensão natural, e a decisão cabe à HU06.

### Estado compartilhado

- Beans são singletons: um campo mutável em `@Service` é uma condição de corrida esperando acontecer.
- Prefira imutabilidade. Se estado compartilhado for inevitável, use `java.util.concurrent` (`ConcurrentHashMap`, `AtomicLong`).
- Evite `ThreadLocal` para contexto de requisição: ele vaza entre requisições em pools e multiplica memória com milhões de threads virtuais.

## 6. Outros recursos úteis

Text block em consultas JPQL:

```java
@Query("""
		select p from Processo p
		where p.status = :status
		order by p.createdAt desc
		""")
List<Processo> buscarPorStatus(StatusProcesso status);
```

- **`_` para o que não se usa:** `(primeiro, _) -> primeiro`, `catch (NumberFormatException _)`.
- **Strings:** `"%s".formatted(...)`; `strip()` (ciente de Unicode) em vez de `trim()`; `isBlank()`.
- **Scripts e experimentos:** `java Script.java` executa um arquivo compacto (`void main() { IO.println(...); }`), útil para testar um algoritmo, como o dígito do CNJ, sem mexer no projeto.
- **`import module java.base;`** é bem-vindo nesses scripts, mas não no código de produção: esconde a origem de cada tipo e pode causar ambiguidade.

## 7. Armadilhas

| Evite | Prefira | Por quê |
|---|---|---|
| `double` ou `float` para dinheiro | `BigDecimal` a partir de `String` | Erro de ponto flutuante binário |
| `BigDecimal.equals` para comparar valores | `compareTo`, ou escala normalizada | `10.0` e `10.00` são diferentes no `equals` |
| `Date`, `Calendar`, `SimpleDateFormat` | `java.time` | Mutáveis e não thread-safe |
| `LocalDate.now()` no domínio | `LocalDate.now(clock)` | Testabilidade e fuso explícito |
| `Optional.get()`; `Optional` em campo ou parâmetro | `orElseThrow`; sobrecarga ou `null` documentado | `NoSuchElementException` e API confusa |
| Coleção `null` como retorno | Coleção vazia | `NullPointerException` em quem chama |
| `catch (Exception e) {}`, `printStackTrace()` | Tratar, ou propagar com a causa; log via SLF4J | Erro silencioso ou fora do log |
| `ThreadLocal` para contexto | `ScopedValue` (`TenantContext`) | Vazamento entre requisições |
| Campo estático que depende de outro declarado depois | Constantes antes das instâncias | Falha na inicialização da classe |
| `Random` para tokens e códigos | `SecureRandom` | Previsibilidade |
| `toUpperCase()` sem `Locale` em identificadores técnicos | `toUpperCase(Locale.ROOT)` | Regras de idioma, como o "i" turco |
| Lombok | Records e código explícito | O projeto não usa; evita processamento de anotações |
