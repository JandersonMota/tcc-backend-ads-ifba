# Comunicação entre módulos, auditoria e portabilidade de dados

## Quando usar cada forma

| Situação | Mecanismo |
|---|---|
| A resposta é necessária imediatamente (ex.: validar que o cliente existe ao criar processo) | **Chamada síncrona** pela API pública do módulo (interface Java no pacote raiz) |
| Efeito colateral que não precisa acontecer na mesma transação (notificar, auditar) | **Evento de domínio** |

Nunca acesse repositório, entidade ou tabela de outro módulo.

## Eventos de domínio

- Eventos são `record` no **pacote raiz** do módulo que publica (são parte da API pública).
- Nome no passado, descrevendo o fato: `AtividadeDelegadaEvent`, `PagamentoRegistradoEvent`.
- Carregam **IDs e dados mínimos**, incluindo `tenantId` e o usuário que executou a ação (o consumidor roda fora da requisição original e não tem o JWT).

```java
package br.com.juridora.atividade;

public record AtividadeDelegadaEvent(
        UUID tenantId,
        UUID atividadeId,
        UUID delegadoPara,
        UUID executadoPor,
        Instant ocorridoEm) {}
```

Publicação, dentro do caso de uso transacional:

```java
@Transactional
public void executar(DelegarAtividadeComando comando) {
    var atividade = atividades.buscar(comando.atividadeId());
    atividade.delegarPara(comando.usuarioId());
    atividades.salvar(atividade);
    eventos.publishEvent(new AtividadeDelegadaEvent(/* ... */));
}
```

Consumo em outro módulo:

```java
package br.com.juridora.notificacao.adaptador.entrada.evento;

@Component
class AtividadeDelegadaListener {

    private final TenantContext tenantContext;
    private final NotificarUsuarioUseCase notificar;   // @Transactional

    // construtor omitido

    @Async
    @TransactionalEventListener   // após o commit; SEM @Transactional aqui
    void aoDelegarAtividade(AtividadeDelegadaEvent evento) {
        tenantContext.executarComo(evento.tenantId(), evento.executadoPor(),
                () -> notificar.executar(evento.delegadoPara(), evento.atividadeId()));
    }
}
```

- Os eventos implementam `compartilhado.dominio.EventoDominio` (`tenantId()`, `ocorridoEm()`).
- O listener executa **após o commit**, de forma assíncrona (`@Async` + `@TransactionalEventListener`; habilitar `@EnableAsync`).
- Usar o **Event Publication Registry** do Spring Modulith (`spring-modulith-starter-jpa`), que persiste a publicação e permite reprocessar eventos cujo consumidor falhou, garantindo que não sejam perdidos.
- Como o consumidor não tem JWT, ele **define o tenant a partir do `tenantId` do evento** com `TenantContext.executarComo` e só então chama o caso de uso `@Transactional`.
- **Não use `@ApplicationModuleListener` em listeners que acessam dados**: ele abre a transação (`REQUIRES_NEW`) *antes* do corpo do método, quando ainda não há tenant, e o `executarComo` falha com `IllegalStateException` (ver `multi-tenancy.md`, "Tenant antes da transação").

Exemplos do projeto:

```
atividade  ──(AtividadeDelegadaEvent)───► notificacao
financeiro ──(PagamentoRegistradoEvent)──► auditoria
```

## Auditoria

- Módulo `auditoria`, implementado como **listener de eventos de domínio** publicados pelos demais módulos (em `adaptador/entrada/evento`).
- Grava na tabela `registro_auditoria` (com `tenant_id` e RLS, como qualquer tabela de negócio):

| Coluna | Descrição |
|---|---|
| `id` | Identificador do registro |
| `tenant_id` | Escritório do registro |
| `usuario_id` | Usuário que executou a ação |
| `acao` | Criação, alteração, exclusão etc. |
| `entidade` / `entidade_id` | Entidade afetada e seu identificador |
| `ocorrido_em` | Data/hora da operação |
| `dados_alterados` | Dados alterados (JSONB, antes/depois) |

- Registros de auditoria são **somente inserção**: não há endpoint de alteração ou exclusão.
- Toda operação relevante (criação, alteração e exclusão de dados de negócio, mudanças de permissão, pagamentos, exportações/backup) deve gerar evento auditável.

## Backup e portabilidade de dados

- O backup oferecido pelo sistema é dos **dados do escritório** (todos os registros do seu `tenant_id`), não do banco inteiro.
- Backup e exportação são unificados no módulo **`portabilidade`** (estrutura simplificada).
- `portabilidade` **não lê tabelas de outros módulos diretamente**: obtém os dados pela API pública de cada módulo (ex.: método de exportação por tenant).
- Acesso restrito a quem tem a permissão correspondente (ex.: proprietário do escritório), e a operação gera evento de auditoria.
- Formato do arquivo (JSON, CSV compactado etc.) ainda não foi definido: pergunte ao usuário.
- Backup do banco inteiro (`pg_dump`, snapshot) é **infraestrutura**: não implemente como funcionalidade de negócio nem exponha a usuários.
