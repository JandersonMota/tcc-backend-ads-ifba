package br.com.juridora.identidade.adaptador.entrada.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Grupo novo do usuário (HU10). */
record TrocarGrupoRequest(@NotNull UUID grupoId) {
}
