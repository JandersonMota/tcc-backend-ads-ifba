# Performance e operação

Regra geral: **meça antes de otimizar**. Otimização sem medida troca clareza por um ganho que talvez não exista. No MVP, o maior risco de desempenho é a consulta ao banco (N+1, falta de índice, listagem sem limite), não a JVM.

## Sumário

1. Como medir
2. Banco de dados e JPA
3. Pool de conexões
4. Threads virtuais
5. Cache
6. JVM em contêiner
7. Observabilidade
8. Imagem Docker
9. Checklist

## 1. Como medir

SQL gerado e parâmetros, só no perfil local:

```yaml
# application-local.yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true   # contagem de consultas por sessão no log
```

- **Plano da consulta:** `EXPLAIN (ANALYZE, BUFFERS) <consulta>` no PostgreSQL. Procure `Seq Scan` em tabela grande e estimativas muito diferentes do real.
- **Consultas mais caras** em ambiente compartilhado: a extensão `pg_stat_statements`.
- **Métricas:** Actuator (`/actuator/metrics/http.server.requests`, `hikaricp.connections.*`, `jvm.*`).
- **Profiling:** JDK Flight Recorder, com baixo custo e próprio para produção.

```powershell
jcmd <pid> JFR.start duration=60s filename=juridora.jfr
```

  Analise o arquivo no JDK Mission Control ou com `jfr print --events jdk.ExecutionSample juridora.jfr`.

## 2. Banco de dados e JPA

- **N+1:** uma consulta para a lista e mais uma por item.
  - Detecte pela contagem de SQL no log.
  - Corrija com `join fetch`/`@EntityGraph` ou com projeção em record.
  - Mitigue com `hibernate.default_batch_fetch_size` (detalhes em `spring-boot-4.md`, seção 5).
- **Paginação:**
  - toda listagem é paginada e tem tamanho máximo;
  - com offset, as páginas finais de tabelas enormes ficam lentas;
  - se isso aparecer, use keyset (`where (created_at, id) < (:ultimoCriado, :ultimoId) order by created_at desc, id desc`).
- **Índices:**
  - toda consulta de negócio filtra por `tenant_id` (filtro do Hibernate e RLS), então índices compostos começam por ele;
  - crie índices para os filtros e a ordenação reais das telas, não para todas as colunas;
  - cada índice custa escrita.
- **Projeções** para telas de listagem: busque só as colunas exibidas, sem carregar o agregado inteiro.
- **Escrita em lote** (importação, portabilidade):

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

  Com os UUIDs gerados pela aplicação, o batching funciona (com IDENTITY, não funcionaria). Processe em lotes (`Gatherers.windowFixed`) com `flush()` e `clear()` entre eles, para o contexto de persistência não crescer sem limite.
- **Transações curtas.** O `set_config('app.tenant_id')` do `TenantJpaTransactionManager` custa uma ida ao banco por transação: abrir dezenas de transações pequenas em laço é pior que uma transação com o lote.

## 3. Pool de conexões

- **HikariCP 7** vem por padrão, com `maximum-pool-size` 10. Mais conexões não significa mais vazão: o PostgreSQL degrada com muitas conexões ativas. A heurística do HikariCP, `(núcleos × 2) + discos`, é um bom ponto de partida para medir.
- **Vazamento de conexão** (transação aberta esperando chamada externa, stream não fechado): em desenvolvimento, ative `spring.datasource.hikari.leak-detection-threshold: 20000` (em milissegundos).
- **`connection-timeout`** curto faz uma sobrecarga aparecer como erro rápido, e não como fila invisível.
- **Pool com uma única conexão** (`maximum-pool-size=1`) é o que o `IsolamentoMultiTenantIT` usa para provar que o tenant não vaza entre transações. Mantenha esse teste.

## 4. Threads virtuais

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

- **Quando ativar:** se houver muitas requisições concorrentes esperando E/S (banco, APIs de tribunais, e-mail). No MVP, o ganho provavelmente não é mensurável: ative quando a medição mostrar threads do Tomcat esgotadas.
- **O limite continua sendo o pool do banco.** Mil threads virtuais com dez conexões formam fila no Hikari.
- **`ScopedValue` combina com threads virtuais.** `ThreadLocal` com cache pesado, não. Fixação de thread ainda ocorre em chamadas nativas; diagnostique com o evento JFR `jdk.VirtualThreadPinned`.

## 5. Cache

- **Só depois de medir**, e só para dados lidos muito e alterados pouco (catálogo de permissões, categorias, configurações do escritório).
- **Abstração:** Spring Cache (`spring-boot-starter-cache`, gerenciado pelo Boot) com Caffeine em memória. As duas são dependências novas: proponha antes.
- **Multi-tenant:** a chave do cache **precisa** incluir o tenant. Um cache chaveado só pelo ID do recurso devolve dados de um escritório para outro, por fora do filtro do Hibernate e do RLS. Inclua `tenantContext.tenantAtual()` na chave (`KeyGenerator` próprio) e teste o isolamento também com cache.
- **Guarde records imutáveis** (resumos), nunca entidades JPA, que têm proxies lazy e ficam desanexadas da sessão.
- **Defina expiração e invalidação** ao alterar. Cache sem invalidação é bug com atraso.

## 6. JVM em contêiner

- **Limites de memória:** a JVM respeita os limites do contêiner. O `MaxRAMPercentage` padrão é 25% da memória, conservador para um contêiner dedicado: use `-XX:MaxRAMPercentage=75`.
- **GC:** o G1 (padrão) serve para o MVP.
  - Para latência muito baixa, há ZGC (`-XX:+UseZGC`, só geracional desde o JDK 24) e Shenandoah geracional (produto no JDK 25).
  - Troque só com medição.
- **Cabeçalhos de objeto compactos:** `-XX:+UseCompactObjectHeaders` (produto no 25, desligado por padrão) reduz o consumo de heap em aplicações com muitos objetos pequenos. Meça antes e depois.
- **Inicialização mais rápida com cache AOT** (JDK 24/25):
  1. Faça um treino, rodando a aplicação com carga representativa: `java -XX:AOTCacheOutput=app.aot -jar app.jar`. O cache é gravado ao encerrar.
  2. Use o cache nas execuções seguintes: `java -XX:AOTCache=app.aot -jar app.jar`.
  3. O classpath e o JDK precisam ser os mesmos do treino.

  Isso vale mais em escala horizontal e testes; para o MVP, é opcional.

## 7. Observabilidade

- **Health:**
  - `/actuator/health`, com grupos `liveness` e `readiness` para orquestradores (`management.endpoint.health.probes.enabled: true`);
  - o banco entra no readiness automaticamente.
- **Métricas:** Micrometer via Actuator. Para Prometheus ou Grafana, o registry correspondente é dependência nova.
- **Rastreamento:**
  - o `spring-boot-starter-opentelemetry` (gerenciado pelo Boot 4) é o caminho para traces distribuídos;
  - o `spring-modulith-observability` do projeto já registra a passagem entre módulos.
- **Logs estruturados:** `logging.structured.format.console: ecs` (ou `logstash`) produz JSON pronto para agregadores. Use no perfil de produção, não no local.
- **Contexto no log:**
  - `traceId` e `spanId` entram automaticamente com tracing;
  - para incluir o tenant, um filtro pode colocá-lo no MDC (é um UUID, não um dado pessoal);
  - o usuário também é UUID, mas avalie com o usuário.
- **Encerramento gracioso** é padrão desde o Boot 3.4: requisições em andamento terminam antes de a aplicação parar (`spring.lifecycle.timeout-per-shutdown-phase`).

## 8. Imagem Docker

A opção mais simples, sem Dockerfile, usa Buildpacks:

```powershell
.\mvnw.cmd spring-boot:build-image
```

Com Dockerfile, extraia o jar em camadas. O comando `extract` e as quatro pastas que ele gera foram conferidos com o jar deste projeto; o build da imagem em si não foi executado.

```dockerfile
FROM eclipse-temurin:25-jre AS extracao
WORKDIR /app
COPY target/juridora-api-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extraido

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 juridora
USER juridora
WORKDIR /app
COPY --from=extracao /app/extraido/dependencies/ ./
COPY --from=extracao /app/extraido/spring-boot-loader/ ./
COPY --from=extracao /app/extraido/snapshot-dependencies/ ./
COPY --from=extracao /app/extraido/application/ ./
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
```

- **Camadas ordenadas** da que menos muda para a que mais muda: um build que só alterou código reaproveita a camada de dependências.
- **Usuário sem privilégios** (`USER`), nunca root.
- **Configuração e segredos** entram por variáveis de ambiente no `docker run`/compose, nunca dentro da imagem.

## 9. Checklist

- [ ] A otimização tem medida antes e depois (tempo, número de consultas, memória).
- [ ] Listagens paginadas, com limite e índice para o filtro e a ordenação.
- [ ] Sem N+1 nas telas principais (contagem de SQL conferida no log).
- [ ] Nenhuma chamada externa dentro de transação; timeouts definidos.
- [ ] Cache, se houver, com o tenant na chave, expiração e invalidação.
- [ ] Imagem com usuário sem privilégios e sem segredos.
