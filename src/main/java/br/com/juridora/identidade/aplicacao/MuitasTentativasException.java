package br.com.juridora.identidade.aplicacao;

import java.time.Duration;

import br.com.juridora.compartilhado.excecao.LimiteExcedidoException;

/** Login bloqueado temporariamente por excesso de falhas (HU06, critério 3, RNF04). */
public class MuitasTentativasException extends LimiteExcedidoException {

	public MuitasTentativasException(Duration aguardar) {
		super("muitas-tentativas", "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.", aguardar);
	}

}
