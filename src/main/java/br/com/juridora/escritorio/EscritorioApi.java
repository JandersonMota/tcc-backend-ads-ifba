package br.com.juridora.escritorio;

import java.util.UUID;

/**
 * API pública do módulo escritorio.
 * <p>
 * O escritório é o próprio tenant. Quem cadastra (módulo identidade) gera o ID antes
 * da transação e o define como tenant da execução (ADR-013).
 */
public interface EscritorioApi {

	/**
	 * Cadastra um escritório no plano Free, ativo, e publica {@link EscritorioCadastradoEvent}.
	 * Participa da transação de quem chama.
	 */
	void cadastrar(UUID id, String nome);

}
