package br.com.juridora.escritorio.aplicacao.casodeuso;

import java.time.Clock;
import java.util.UUID;

import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.escritorio.EscritorioCadastradoEvent;
import br.com.juridora.escritorio.dominio.modelo.Escritorio;
import br.com.juridora.escritorio.dominio.modelo.NomeEscritorio;
import br.com.juridora.escritorio.dominio.repositorio.EscritorioRepositorio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastra o escritório (tenant) no plano Free – HU05. */
@Service
class CadastrarEscritorioCasoDeUso implements EscritorioApi {

	private final EscritorioRepositorio escritorios;
	private final ApplicationEventPublisher eventos;
	private final Clock relogio = Clock.systemUTC();

	CadastrarEscritorioCasoDeUso(EscritorioRepositorio escritorios, ApplicationEventPublisher eventos) {
		this.escritorios = escritorios;
		this.eventos = eventos;
	}

	@Override
	@Transactional
	public void cadastrar(UUID id, String nome) {
		Escritorio escritorio = Escritorio.cadastrar(id, new NomeEscritorio(nome), relogio.instant());
		escritorios.adicionar(escritorio);
		eventos.publishEvent(new EscritorioCadastradoEvent(escritorio.getId(), escritorio.getNome(),
				escritorio.getCreatedAt()));
	}

}
