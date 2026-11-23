package br.com.juridora.identidade.aplicacao;

/** Dados editáveis do usuário (HU08). Grupo é a HU10; senha fica fora do MVP. */
public record AlterarUsuarioComando(String nome, String email) {
}
