package br.com.juridora.escritorio;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.dominio.EventoDominio;

/** Um escritório (tenant) foi cadastrado (HU05). */
public record EscritorioCadastradoEvent(UUID tenantId, String nome, Instant ocorridoEm) implements EventoDominio {
}
