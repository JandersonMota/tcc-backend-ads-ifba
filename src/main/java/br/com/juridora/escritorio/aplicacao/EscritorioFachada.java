package br.com.juridora.escritorio.aplicacao;

import java.util.UUID;

import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.escritorio.LimitesDoPlano;
import br.com.juridora.escritorio.aplicacao.casodeuso.CadastrarEscritorioCasoDeUso;
import br.com.juridora.escritorio.aplicacao.consulta.ConsultarLimitesDoPlanoCasoDeUso;
import org.springframework.stereotype.Service;

/**
 * Implementa a {@link EscritorioApi} delegando a um caso de uso por operação, para que
 * cada caso de uso continue com uma responsabilidade só.
 */
@Service
class EscritorioFachada implements EscritorioApi {

	private final CadastrarEscritorioCasoDeUso cadastrar;

	private final ConsultarLimitesDoPlanoCasoDeUso consultarLimites;

	EscritorioFachada(CadastrarEscritorioCasoDeUso cadastrar, ConsultarLimitesDoPlanoCasoDeUso consultarLimites) {
		this.cadastrar = cadastrar;
		this.consultarLimites = consultarLimites;
	}

	@Override
	public void cadastrar(UUID id, String nome) {
		cadastrar.cadastrar(id, nome);
	}

	@Override
	public LimitesDoPlano limitesDoPlano() {
		return consultarLimites.limitesDoPlano();
	}

}
