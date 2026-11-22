# Spring Boot 4 e Spring Framework 7 na prática

Os exemplos usam o módulo `processo`, foram compilados contra as dependências do projeto e passam no `CamadasTest`, no `ModularidadeTest` e em testes de integração com PostgreSQL. Os imports foram omitidos, exceto quando o pacote é a novidade.

## Sumário

1. O que muda em relação a tutoriais do Boot 2 e 3
2. Camada web: controllers, DTOs e paginação
3. Erros com `ProblemDetail`
4. Casos de uso e transações
5. JPA e Hibernate
6. Configuração
7. Integrações HTTP

## 1. O que muda em relação a tutoriais do Boot 2 e 3

| Tema | Neste projeto (Boot 4.1 / Framework 7) |
|---|---|
| Starters | Modulares: `spring-boot-starter-webmvc` (não `-web`), `-flyway`, e starters de teste por tecnologia (`-webmvc-test`, `-data-jpa-test`) |
| Anotações de teste | Mudaram de pacote: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` |
| Mocks em testes Spring | `@MockitoBean` e `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`). `@MockBean` não existe mais |
| JSON | Jackson 3 (`tools.jackson.databind.json.JsonMapper`). As anotações continuam em `com.fasterxml.jackson.annotation`. O Jackson 2 presente vem do SpringDoc: não o use no código |
| HTTP 422 | `HttpStatus.UNPROCESSABLE_CONTENT` (`UNPROCESSABLE_ENTITY` está deprecated) |
| Cliente HTTP | `RestClient`. O `RestClient.Builder` autoconfigurado fica no `spring-boot-starter-restclient`, que **não** está no `pom.xml`: use `RestClient.builder()` ou proponha o starter |
| Resiliência | `@Retryable` e `@ConcurrencyLimit` (`org.springframework.resilience.annotation`), ativados por `@EnableResilientMethods`. Não precisa do Spring Retry |
| Testes MVC | `MockMvcTester` (AssertJ) e `RestTestClient` |
| Null-safety | JSpecify (`org.jspecify.annotations.Nullable`, `@NullMarked`). O projeto ainda não adotou, e adotar é uma decisão do usuário |
| Versionamento de API | Nativo (`@RequestMapping(version = ...)`). Só introduza se o usuário pedir |
| Spring Security | 7.1, comentado no `pom.xml` até o emissor do JWT ser definido (HU06) |

## 2. Camada web: controllers, DTOs e paginação

```java
@RestController
@RequestMapping("/api/processos")
class ProcessoController {

	private final ConsultarProcessosUseCase consultarProcessos;
	private final ArquivarProcessoUseCase arquivarProcesso;

	ProcessoController(ConsultarProcessosUseCase consultarProcessos, ArquivarProcessoUseCase arquivarProcesso) {
		this.consultarProcessos = consultarProcessos;
		this.arquivarProcesso = arquivarProcesso;
	}

	@GetMapping("/{id}")
	ProcessoResponse buscar(@PathVariable UUID id) {
		return ProcessoResponse.de(consultarProcessos.buscar(id));
	}

	@GetMapping
	PaginaResponse<ProcessoResponse> listar(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pagina) {
		return PaginaResponse.de(consultarProcessos.listar(pagina), ProcessoResponse::de);
	}

	/** Ação de negócio como sub-recurso, em vez de PATCH genérico no status. */
	@PostMapping("/{id}/arquivamento")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void arquivar(@PathVariable UUID id) {
		arquivarProcesso.executar(id);
	}

}
```

```java
record ProcessoResponse(UUID id, String numero, String status, UUID clienteId) {

	static ProcessoResponse de(ProcessoResumo resumo) {
		return new ProcessoResponse(resumo.id(), resumo.numero(), resumo.status().name(), resumo.clienteId());
	}

}
```

- **O controller é fino.** Ele traduz HTTP para o caso de uso e de volta, sem regra de negócio e sem repositório (o `CamadasTest` barra).
- **Quando o Spring Security entrar (HU06),** cada método ganha `@PreAuthorize("hasAuthority('PROCESSO_...')")`.
- **Ações de negócio** (arquivar, delegar, registrar pagamento) viram sub-recursos com `POST`. Assim cada ação tem permissão, auditoria e validação próprias.
- **Nunca exponha a entidade.** Isso dispara lazy loading fora da transação, vaza campos internos e acopla a API ao schema.

### Status HTTP

| Situação | Status |
|---|---|
| Criado | 201 + `Location` |
| Consulta ou alteração com corpo | 200 |
| Ação ou exclusão sem corpo | 204 |
| JSON malformado ou campo inválido | 400 |
| Não autenticado / sem permissão | 401 / 403 |
| Não existe ou é de outro escritório | 404 |
| Conflito de estado, edição concorrente ou duplicidade | 409 |
| Regra de negócio violada | 422 |

Se o projeto ainda não tiver fixado 409 e 422, proponha esta tabela e registre a decisão.

### Paginação

```java
/**
 * Página de resultados com contrato estável. Serializar o {@code Page} do Spring
 * Data diretamente expõe detalhes internos (e o próprio Spring Data avisa contra isso).
 */
public record PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

	public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<? super E, ? extends T> conversor) {
		return new PaginaResponse<>(pagina.<T>map(conversor).getContent(), pagina.getNumber(), pagina.getSize(),
				pagina.getTotalElements(), pagina.getTotalPages());
	}

}
```

- **Onde fica:** `compartilhado.infraestrutura.web`. O type witness `<T>` em `map` é necessário: sem ele, a inferência de tipos não compila.
- **Alternativa pronta:** `spring.data.web.pageable.serialization-mode: via-dto` usa o `PagedModel` do Spring Data.
- **Limite o tamanho da página** com `spring.data.web.pageable.max-page-size: 100` (o padrão é 2000).
- **Restrinja a ordenação.** Valide as propriedades de `sort` contra uma lista permitida. Uma propriedade inexistente gera `PropertyReferenceException` (500), e uma propriedade interna permite inferir dados.

### Validação na borda

```java
/** Formato validado na borda (400); regras de negócio ficam no domínio. Sem tenantId: ele vem do JWT. */
record CadastrarClientePessoaJuridicaRequest(
		@NotBlank @Size(max = 200) String razaoSocial,
		@NotBlank @CNPJ(format = CNPJ.Format.ALPHANUMERIC) String cnpj,
		@Email @Size(max = 254) String email) {
}
```

- **Ative a validação** com `@Valid @RequestBody`. Os erros saem como 400 com o mapa de campos (seção 3).
- **CNPJ alfanumérico:** a Receita Federal emite CNPJ com letras desde julho de 2026. `@CNPJ(format = ALPHANUMERIC)` (`org.hibernate.validator.constraints.br`) aceita os dois formatos, com ou sem máscara (testado com `11.222.333/0001-81` e `12.ABC.345/01DE-35`). Guarde em `VARCHAR(14)`, sem máscara e em maiúsculas; nunca como número.
- **CPF:** use `@CPF`.

## 3. Erros com `ProblemDetail`

Exceções base em `compartilhado.excecao`. O pacote existe e está vazio; se ainda não houver hierarquia, proponha esta:

| Exceção | Status | Exemplo |
|---|---|---|
| `RecursoNaoEncontradoException` | 404 | Processo inexistente ou de outro escritório |
| `RegraDeNegocioException` | 422 | Arquivar processo já arquivado; subclasses como `NumeroProcessoInvalidoException` |
| `ConflitoException` | 409 | Número de processo já cadastrado no escritório |

Todas estendem `RuntimeException` e recebem só a mensagem. Unchecked importa por dois motivos: provoca rollback por padrão e não polui as assinaturas.

```java
package br.com.juridora.compartilhado.infraestrutura.web;

/**
 * Traduz exceções em respostas RFC 9457 ({@link ProblemDetail}).
 * <p>
 * O domínio lança exceções sem saber de HTTP; o status é decidido só aqui.
 * As exceções do Spring MVC (JSON malformado, rota inexistente...) ficam com a
 * classe base, que já responde com {@code ProblemDetail}.
 */
@RestControllerAdvice
class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	ProblemDetail recursoNaoEncontrado(RecursoNaoEncontradoException ex) {
		return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
	}

	@ExceptionHandler(RegraDeNegocioException.class)
	ProblemDetail regraDeNegocio(RegraDeNegocioException ex) {
		return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada", ex.getMessage());
	}

	@ExceptionHandler(ConflitoException.class)
	ProblemDetail conflito(ConflitoException ex) {
		return problema(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail edicaoConcorrente(OptimisticLockingFailureException ex) {
		return problema(HttpStatus.CONFLICT, "Edição concorrente",
				"O registro foi alterado por outro usuário. Recarregue e tente novamente.");
	}

	/** Bean Validation nos {@code *Request}: devolve 400 com o erro de cada campo. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problema = ProblemDetail.forStatusAndDetail(status, "Há campos inválidos na requisição.");
		problema.setTitle("Requisição inválida");
		problema.setProperty("campos", ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(FieldError::getField,
						erro -> Objects.requireNonNullElse(erro.getDefaultMessage(), "inválido"),
						(primeiro, _) -> primeiro)));
		return ResponseEntity.status(status).headers(headers).body(problema);
	}

	/**
	 * Último recurso: stack trace só no log; o cliente recebe 500 sem detalhes internos.
	 * Quando o Spring Security entrar (HU06), declare handlers para AccessDeniedException (403)
	 * e AuthenticationException (401): o handler mais específico vence, e sem eles este
	 * método transformaria as duas em 500.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail erroInesperado(Exception ex) {
		log.error("Erro inesperado ao processar a requisição", ex);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
				"Ocorreu um erro inesperado. Tente novamente mais tarde.");
	}

	private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
		var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
		problema.setTitle(titulo);
		return problema;
	}

}
```

- **A resposta nunca carrega** stack trace, SQL, nome de tabela ou de classe.
- **Duplicidade:** verifique no caso de uso (`ConflitoException`) e mantenha a `UNIQUE` no banco como rede de segurança.

## 4. Casos de uso e transações

```java
@Service
@Transactional(readOnly = true)
class ConsultarProcessos implements ConsultarProcessosUseCase {

	private final ProcessoRepository processos;

	ConsultarProcessos(ProcessoRepository processos) {
		this.processos = processos;
	}

	@Override
	public ProcessoResumo buscar(UUID id) {
		return processos.buscarPorId(id)
				.map(ProcessoResumo::de)   // converte DENTRO da transação (open-in-view: false)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado"));
	}

	@Override
	public Page<ProcessoResumo> listar(Pageable pagina) {
		return processos.listar(pagina).map(ProcessoResumo::de);
	}

}
```

```java
@Service
class ArquivarProcesso implements ArquivarProcessoUseCase {

	private final ProcessoRepository processos;
	private final TenantContext tenantContext;
	private final ApplicationEventPublisher eventos;
	private final Clock relogio;

	// construtor com os quatro parâmetros

	@Override
	@Transactional
	public void executar(UUID processoId) {
		var processo = processos.buscarPorId(processoId)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado"));
		processo.arquivar();
		processos.salvar(processo);
		eventos.publishEvent(new ProcessoArquivadoEvent(processo.getTenantId(), processo.getId(),
				tenantContext.usuarioOpcional().orElse(null), Instant.now(relogio)));
	}

}
```

- **A transação abre no caso de uso.** Controllers e listeners não são transacionais, porque o tenant precisa estar definido antes (skill de arquitetura).
- **`readOnly = true` nas consultas.** O Hibernate pula a verificação de alterações e o flush, e a intenção fica explícita.
- **Autoinvocação não passa pelo proxy.** Um método da mesma classe chamando outro anotado com `@Transactional` roda sem a anotação.
- **Rollback padrão** acontece só com exceções unchecked.
- **Nada de HTTP ou e-mail dentro da transação.** A conexão e os locks ficam presos enquanto a chamada externa espera. E, se houver rollback, o e-mail já terá saído. Efeitos colaterais vão em eventos tratados após o commit.
- **`Clock` vem de um bean único** (`compartilhado.infraestrutura.RelogioConfiguracao`, com `Clock.systemUTC()`). Nos testes, use `Clock.fixed(...)`.

## 5. JPA e Hibernate

```java
@Entity
@Table(name = "processo")
public class Processo extends EntidadeTenant {

	@Column(nullable = false, length = 20)
	private NumeroProcesso numero;       // convertido pelo NumeroProcessoConverter (autoApply)

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusProcesso status;

	@Column(nullable = false)
	private UUID clienteId;              // agregado de outro módulo: só o ID, nunca @ManyToOne

	@Version
	private long versao;                 // bloqueio otimista: edição concorrente vira 409

	protected Processo() {
		// exigido pelo JPA
	}

	public Processo(NumeroProcesso numero, UUID clienteId) {
		this.numero = Objects.requireNonNull(numero, "numero é obrigatório");
		this.clienteId = Objects.requireNonNull(clienteId, "clienteId é obrigatório");
		this.status = StatusProcesso.ATIVO;
	}

	/** Muda o estado por um método com nome do negócio, que protege a invariante. */
	public void arquivar() {
		if (status == StatusProcesso.ARQUIVADO) {
			throw new RegraDeNegocioException("O processo já está arquivado");
		}
		status = StatusProcesso.ARQUIVADO;
	}

	// getters, sem setters
}
```

```java
package br.com.juridora.processo.adaptador.saida.persistencia;

/**
 * Mapeia o value object para {@code VARCHAR(20)}. Com {@code autoApply}, a entidade
 * não referencia o conversor: o domínio continua sem depender do adaptador.
 */
@Converter(autoApply = true)
class NumeroProcessoConverter implements AttributeConverter<NumeroProcesso, String> {

	@Override
	public String convertToDatabaseColumn(NumeroProcesso numero) {
		return numero == null ? null : numero.digitos();
	}

	@Override
	public NumeroProcesso convertToEntityAttribute(String digitos) {
		return digitos == null ? null : new NumeroProcesso(digitos);
	}

}
```

### Por que o conversor fica no adaptador

Com `@Convert(converter = ...)` na entidade, o domínio passaria a depender do adaptador, e o `CamadasTest` quebraria. Com `autoApply`, o scan do Spring encontra o conversor sozinho, mesmo package-private. O repositório Spring Data (`interface ProcessoJpaRepository extends JpaRepository<Processo, UUID>`) também pode ser package-private, e a classe `@Repository` que implementa a porta delega para ele.

### Mapeamento

- **`@ManyToOne` é EAGER por padrão:** declare sempre `fetch = FetchType.LAZY`.
- **Filhos do agregado** usam `@OneToMany(mappedBy = ..., cascade = ALL, orphanRemoval = true)`. Nunca use cascade entre agregados; entre módulos, só o ID.
- **Enums** sempre com `EnumType.STRING`. Com `ORDINAL`, reordenar o enum corrompe os dados.
- **`@Version`** exige `versao BIGINT NOT NULL DEFAULT 0` na migração. O conflito chega como `OptimisticLockingFailureException`, que o tratador traduz para 409.
- **`equals` e `hashCode`** seguem o `EntidadeTenant` (id + classe). Não gere com todos os campos.

### N+1 e consultas

- **O sintoma:** listagem que acessa uma associação por linha. Corrija com `join fetch` ou `@EntityGraph`, ou com projeção em record (`select new ...Resumo(...)`).
- **Paginação com `join fetch` de coleção** faz o Hibernate paginar em memória (ele avisa no log). Pagine os IDs primeiro e busque os detalhes depois, ou use projeção.
- **`spring.jpa.properties.hibernate.default_batch_fetch_size: 50`** mitiga o N+1 em carregamentos lazy.
- **Prefira JPQL e Spring Data.** SQL nativo não recebe o filtro do `@TenantId`; nele, só o RLS protege.
- **Lotes de `INSERT`:** os UUIDs gerados pela aplicação permitem batching JDBC (`hibernate.jdbc.batch_size`, `hibernate.order_inserts`); IDENTITY desligaria o batching.

### Migração

- `UNIQUE (tenant_id, numero)` já cria um índice iniciado por `tenant_id`.
- Crie um índice composto para a ordenação padrão da listagem: `(tenant_id, created_at DESC)`.
- Use `CHECK` para enums: `status IN ('ATIVO', 'SUSPENSO', 'ARQUIVADO')`.

## 6. Configuração

```java
/** {@code juridora.tribunal.*} no application.yaml; validado na inicialização (falha cedo). */
@ConfigurationProperties("juridora.tribunal")
@Validated
public record TribunalProperties(
		@NotBlank String url,
		@DefaultValue("5s") Duration timeoutConexao,
		@DefaultValue("10s") Duration timeoutLeitura) {
}
```

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TribunalProperties.class)
@EnableResilientMethods   // ativa @Retryable/@ConcurrencyLimit (Spring Framework 7)
class TribunalConfiguracao {
}
```

- **Prefira propriedades tipadas** em record a `@Value` espalhado: um erro de configuração derruba a aplicação na inicialização, e não no primeiro uso.
- **Perfis:** `application-local.yaml` para o banco do docker-compose.
- **Segredos** vêm de variáveis de ambiente (`${JURIDORA_DB_SENHA}`) ou do `docker/.env`, que não é versionado. Nunca ficam no YAML versionado.
- **`@EnableResilientMethods` vale para a aplicação inteira.** Se mais de um módulo precisar, mova para `compartilhado.infraestrutura`.

## 7. Integrações HTTP

```java
@Component
class TribunalHttpGateway implements TribunalGateway {

	private final RestClient http;

	TribunalHttpGateway(TribunalProperties propriedades) {
		var cliente = HttpClient.newBuilder().connectTimeout(propriedades.timeoutConexao()).build();
		var fabrica = new JdkClientHttpRequestFactory(cliente);
		fabrica.setReadTimeout(propriedades.timeoutLeitura());     // sem timeout, uma API lenta prende a thread
		this.http = RestClient.builder()
				.baseUrl(propriedades.url())
				.requestFactory(fabrica)
				.build();
	}

	/** Consulta é idempotente: pode repetir em falha de rede, com espera crescente. */
	@Override
	@Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 500, multiplier = 2)
	public List<MovimentacaoTribunal> buscarMovimentacoes(NumeroProcesso numero) {
		var resposta = http.get()
				.uri("/processos/{numero}/movimentacoes", numero.digitos())   // rota ilustrativa
				.retrieve()
				.body(MovimentacoesResposta.class);
		return resposta == null ? List.of() : resposta.movimentacoes().stream()
				.map(item -> new MovimentacaoTribunal(item.data(), item.descricao()))
				.toList();
	}

	/** Contrato da API externa: fica no adaptador e não vaza para o domínio. */
	record MovimentacoesResposta(List<Item> movimentacoes) {

		record Item(LocalDate data, String descricao) {
		}

	}

}
```

- **A porta (`TribunalGateway`)** fica em `dominio/repositorio`; a implementação, em `adaptador/saida/tribunal`.
- **Defina sempre os dois timeouts,** de conexão e de leitura.
- **Repita só operações idempotentes,** com backoff (`multiplier`). Nunca repita um `POST` que cria algo sem uma chave de idempotência.
- **Construir o `RestClient` dentro do adaptador** evita ambiguidade quando vários módulos tiverem clientes. A contrapartida é perder a observabilidade automática do `RestClient.Builder` do Boot, que exige o `spring-boot-starter-restclient`.
- **Traduza erros externos** (4xx/5xx, timeout) para exceções ou resultados do próprio módulo. O resto do sistema não deve conhecer `RestClientException`.
- **Testes:** o resto do sistema usa uma implementação falsa da porta. O adaptador se testa contra um servidor HTTP falso, e escolher essa biblioteca (WireMock, MockWebServer) é decisão de dependência.
