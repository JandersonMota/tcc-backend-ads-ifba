# Estrutura dos módulos

## Visão geral

```
FRONTEND ──HTTPS──► MONÓLITO MODULAR ──► PostgreSQL
                          │
                          ├──► API de e-mail     (adaptador de saída)
                          ├──► APIs de tribunais (adaptador de saída)
                          └──► Armazenamento     (adaptador de saída)
```

Um único deploy, com módulos de negócio fortemente isolados internamente. A extração futura para microsserviços só é viável se as fronteiras forem respeitadas desde já.

## Fluxo hexagonal

```
Adaptador de entrada (controller REST)
        ↓
Porta de entrada (interface do caso de uso)
        ↓
Aplicação (implementação do caso de uso, transação)
        ↓
Domínio (agregados, invariantes, regras)
        ↓
Porta de saída (interface: repositório, gateway)
        ↓
Adaptador de saída (JPA, cliente HTTP, armazenamento)
```

Dependências apontam **para dentro**: adaptadores conhecem a aplicação e o domínio; o domínio não conhece ninguém.

## Módulo com DDD tático completo

Usado em `escritorio`, `autorizacao`, `processo`, `atividade`, `financeiro`.

```
processo
├── ProcessoApi.java                ← API pública (pacote raiz do módulo)
├── ProcessoCriadoEvent.java        ← eventos publicados também são API pública
│
├── dominio
│   ├── modelo         ← agregados, entidades, value objects
│   ├── servico        ← serviços de domínio (regras que envolvem vários agregados)
│   └── repositorio    ← portas de saída (interfaces)
│
├── aplicacao
│   ├── casodeuso      ← portas de entrada e implementações
│   ├── comando        ← records de entrada que alteram estado
│   └── consulta       ← records/consultas de leitura
│
├── adaptador
│   ├── entrada
│   │   └── web        ← controllers REST, DTOs de request/response
│   └── saida
│       └── persistencia ← entidades JPA, Spring Data, implementação das portas
│
└── configuracao       ← beans do módulo
```

Regras:
- **Agregado** protege suas invariantes; estado só muda por métodos com nome do negócio (`processo.arquivar()`, não `setStatus`).
- Referência a outro agregado (inclusive de outro módulo) **apenas pelo ID** (`ClienteId clienteId`), nunca por objeto.
- **Value objects** como `record` (ex.: `NumeroProcesso`, `Dinheiro`), validando no construtor compacto.
- Comandos e consultas como `record`.
- O caso de uso abre a transação (`@Transactional`), carrega o agregado pela porta, chama o método de domínio, salva e publica eventos.
- Modelo de domínio pode ser separado da entidade JPA quando houver ganho real; para o prazo do TCC, é aceitável anotar o agregado com JPA, desde que o domínio não dependa de outras classes de infraestrutura.

## Módulo simplificado

Usado em `cliente`, `identidade`, `documento`, `notificacao`, `auditoria`, `portabilidade` e cadastros auxiliares (categorias).

```
cliente
├── ClienteApi.java
├── dominio         ← entidade e regras simples
├── aplicacao       ← serviço de aplicação (sem separar comando/consulta)
└── adaptador
    ├── entrada/web
    └── saida/persistencia
```

Mantém a separação entre `dominio`, `aplicacao` e `adaptador`, mas sem agregados elaborados nem CQRS. Variações previstas: `documento` tem também `adaptador/saida/armazenamento`; `notificacao` e `auditoria` têm `adaptador/entrada/evento` (listeners); `portabilidade` não tem domínio próprio.

## API pública do módulo

Spring Modulith considera **API pública apenas os tipos do pacote raiz do módulo**; subpacotes são internos. Portanto:
- Coloque no pacote raiz a interface que outros módulos usam (`ClienteApi`) e os eventos que o módulo publica.
- Se precisar expor um subpacote, anote o `package-info.java` com `@org.springframework.modulith.NamedInterface("nome")`.
- A API pública retorna **records próprios** (ex.: `ClienteResumo`), nunca entidades internas.

```java
package br.com.juridora.cliente;

public interface ClienteApi {
    boolean existe(UUID clienteId);
    Optional<ClienteResumo> buscarResumo(UUID clienteId);
}

public record ClienteResumo(UUID id, String nome) {}
```

## Controller (adaptador de entrada)

```java
package br.com.juridora.processo.adaptador.entrada.web;

@RestController
@RequestMapping("/api/processos")
class ProcessoController {

    private final CriarProcessoCasoDeUso criarProcesso;

    ProcessoController(CriarProcessoCasoDeUso criarProcesso) {
        this.criarProcesso = criarProcesso;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROCESSO_CRIAR')")
    ResponseEntity<ProcessoResponse> criar(@Valid @RequestBody CriarProcessoRequest request) {
        var id = criarProcesso.executar(request.paraComando());
        return ResponseEntity.created(URI.create("/api/processos/" + id)).build();
    }
}
```

- Sem `tenantId` no request: o tenant é resolvido pelo contexto de segurança.
- Controller não acessa repositório nem contém regra de negócio.
- Classes internas do módulo podem ser package-private para reforçar o encapsulamento.

## Adaptadores de saída para integrações externas

E-mail, tribunais e armazenamento ficam atrás de portas definidas no módulo que as usa:

```java
// dominio/repositorio ou aplicacao (porta)
public interface TribunalGateway {
    List<Movimentacao> buscarMovimentacoes(NumeroProcesso numero);
}

// adaptador/saida/tribunal (implementação)
class DatajudTribunalGateway implements TribunalGateway { ... }
```

Isso permite trocar o provedor e usar implementações falsas nos testes.

## Frontend

Estrutura prevista (framework a definir), também em português:

```
src/
├── nucleo, compartilhado, autenticacao, painel
├── clientes, processos, atividades, financeiro
├── documentos, notificacoes
└── usuarios, permissoes, configuracoes
```
