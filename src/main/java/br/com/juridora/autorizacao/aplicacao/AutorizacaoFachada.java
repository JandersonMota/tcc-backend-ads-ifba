package br.com.juridora.autorizacao.aplicacao;

import java.util.Set;
import java.util.UUID;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.autorizacao.aplicacao.casodeuso.CriarGruposPadraoCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarGruposCasoDeUso;
import br.com.juridora.autorizacao.aplicacao.consulta.ConsultarPermissoesDoGrupoCasoDeUso;
import org.springframework.stereotype.Service;

/**
 * Implementa a {@link AutorizacaoApi} delegando a um caso de uso por operação, para que
 * cada caso de uso continue com uma responsabilidade só.
 */
@Service
class AutorizacaoFachada implements AutorizacaoApi {

	private final CriarGruposPadraoCasoDeUso criarGruposPadrao;

	private final ConsultarPermissoesDoGrupoCasoDeUso consultarPermissoes;

	private final ConsultarGruposCasoDeUso consultarGrupos;

	AutorizacaoFachada(CriarGruposPadraoCasoDeUso criarGruposPadrao,
			ConsultarPermissoesDoGrupoCasoDeUso consultarPermissoes, ConsultarGruposCasoDeUso consultarGrupos) {
		this.criarGruposPadrao = criarGruposPadrao;
		this.consultarPermissoes = consultarPermissoes;
		this.consultarGrupos = consultarGrupos;
	}

	@Override
	public UUID criarGruposPadrao() {
		return criarGruposPadrao.criarGruposPadrao();
	}

	@Override
	public Set<String> permissoesDoGrupo(UUID grupoId) {
		return consultarPermissoes.permissoesDoGrupo(grupoId);
	}

	@Override
	public boolean grupoExiste(UUID grupoId) {
		return consultarGrupos.existe(grupoId);
	}

	@Override
	public boolean ehGrupoProprietario(UUID grupoId) {
		return consultarGrupos.ehProprietario(grupoId);
	}

}
