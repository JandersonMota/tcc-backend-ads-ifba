package br.com.juridora.escritorio;

/**
 * Limites do plano do escritório (RN09). Os valores vêm de configuração
 * ({@code juridora.planos.<plano>}); ao atingir um limite, só a criação é bloqueada.
 *
 * @param usuariosAtivos máximo de usuários ativos, contando o proprietário
 * @param clientesAtivos máximo de clientes ativos
 * @param processosAtivos máximo de processos ativos (arquivados não contam)
 */
public record LimitesDoPlano(int usuariosAtivos, int clientesAtivos, int processosAtivos) {

	public LimitesDoPlano {
		if (usuariosAtivos < 1 || clientesAtivos < 0 || processosAtivos < 0) {
			throw new IllegalArgumentException("Limites do plano inválidos");
		}
	}

}
