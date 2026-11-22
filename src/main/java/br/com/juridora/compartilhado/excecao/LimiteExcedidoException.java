package br.com.juridora.compartilhado.excecao;

import java.time.Duration;
import java.util.Objects;

/**
 * Muitas requisições em pouco tempo (HTTP 429). {@link #aguardar()} vira o cabeçalho
 * {@code Retry-After}.
 */
public abstract class LimiteExcedidoException extends NegocioException {

	private final Duration aguardar;

	protected LimiteExcedidoException(String tipo, String mensagem, Duration aguardar) {
		super(tipo, mensagem);
		this.aguardar = Objects.requireNonNull(aguardar, "aguardar é obrigatório");
	}

	/** Tempo até uma nova tentativa ser aceita. */
	public Duration aguardar() {
		return aguardar;
	}

}
