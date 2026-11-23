package br.com.juridora.identidade.adaptador.entrada.agendamento;

import br.com.juridora.identidade.aplicacao.EncerrarSessaoServico;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Remove periodicamente as revogações de tokens já expirados (HU07): depois do
 * {@code exp}, o próprio validador do JWT recusa o token, e o registro não serve mais.
 */
@Component
class LimpezaTokensRevogadosAgendamento {

	private final EncerrarSessaoServico encerrarSessao;

	LimpezaTokensRevogadosAgendamento(EncerrarSessaoServico encerrarSessao) {
		this.encerrarSessao = encerrarSessao;
	}

	@Scheduled(initialDelayString = "${juridora.seguranca.logout.intervalo-limpeza}",
			fixedDelayString = "${juridora.seguranca.logout.intervalo-limpeza}")
	void limpar() {
		encerrarSessao.removerRevogacoesVencidas();
	}

}
