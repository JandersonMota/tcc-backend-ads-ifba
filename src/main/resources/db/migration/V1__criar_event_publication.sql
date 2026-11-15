-- Registro de publicação de eventos do Spring Modulith (spring-modulith-starter-jpa).
-- Tabela de infraestrutura, não de negócio: não tem tenant_id nem RLS. O tenantId
-- vai dentro do evento serializado (ver skill arquitetura-gestao-juridica).
-- Mapeamento: org.springframework.modulith.events.jpa.JpaEventPublication (2.1.x).

CREATE TABLE event_publication (
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT                      NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_event_publication_serialized_event_hash ON event_publication USING hash (serialized_event);
CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);

-- Usada apenas no modo de conclusão "archive"; existe para o Hibernate validar o mapeamento.
CREATE TABLE event_publication_archive (
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT                      NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
