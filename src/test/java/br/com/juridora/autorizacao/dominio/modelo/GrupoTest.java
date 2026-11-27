package br.com.juridora.autorizacao.dominio.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import br.com.juridora.compartilhado.excecao.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** HU09 – regras do agregado Grupo. */
class GrupoTest {

	@Test
	void personalizadoNasceComNomeLimpoEPermissoesInformadas() {
		Grupo grupo = Grupo.personalizado("  Financeiro ", Set.of(Permissao.FINANCEIRO_VISUALIZAR));

		assertThat(grupo.getNome()).isEqualTo("Financeiro");
		assertThat(grupo.isPadrao()).isFalse();
		assertThat(grupo.ehProprietario()).isFalse();
		assertThat(grupo.getPermissoes()).containsExactly(Permissao.FINANCEIRO_VISUALIZAR);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void nomeEmBrancoNaoServe(String nome) {
		assertThatThrownBy(() -> Grupo.personalizado(nome, Set.of())).isInstanceOf(ValidacaoException.class);
		assertThatThrownBy(() -> Grupo.personalizado(null, Set.of())).isInstanceOf(ValidacaoException.class);
	}

	@Test
	void nomeLongoDemaisNaoServe() {
		String longo = "x".repeat(Grupo.TAMANHO_MAXIMO_NOME + 1);

		assertThatThrownBy(() -> Grupo.personalizado(longo, Set.of())).isInstanceOf(ValidacaoException.class);
	}

	@Test
	void personalizadoPodeSerRenomeadoTerPermissoesTrocadasEExcluido() {
		Grupo grupo = Grupo.personalizado("Financeiro", Set.of(Permissao.FINANCEIRO_VISUALIZAR));

		grupo.renomear("Cobrança");
		grupo.definirPermissoes(Set.of(Permissao.CLIENTE_VISUALIZAR, Permissao.FINANCEIRO_CRIAR));

		assertThat(grupo.getNome()).isEqualTo("Cobrança");
		assertThat(grupo.getPermissoes()).containsExactlyInAnyOrder(Permissao.CLIENTE_VISUALIZAR,
				Permissao.FINANCEIRO_CRIAR);
		grupo.garantirExcluivel();
		grupo.definirPermissoes(Set.of());
		assertThat(grupo.getPermissoes()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = GrupoPadrao.class, names = "PROPRIETARIO", mode = EnumSource.Mode.EXCLUDE)
	void grupoPadraoQueNaoEOProprietarioERenomeadoEExcluido(GrupoPadrao definicao) {
		Grupo grupo = Grupo.padrao(definicao);

		grupo.renomear("  Outro ");
		grupo.garantirExcluivel();

		assertThat(grupo.getNome()).isEqualTo("Outro");
		assertThat(grupo.isPadrao()).isTrue();
		assertThat(grupo.ehProprietario()).isFalse();
	}

	@Test
	void proprietarioNaoERenomeadoNemExcluido() {
		Grupo proprietario = Grupo.padrao(GrupoPadrao.PROPRIETARIO);

		assertThatThrownBy(() -> proprietario.renomear("Outro"))
				.isInstanceOf(GrupoProprietarioProtegidoException.class);
		assertThatThrownBy(proprietario::garantirExcluivel).isInstanceOf(GrupoProprietarioProtegidoException.class);
		assertThat(proprietario.getNome()).isEqualTo(GrupoPadrao.PROPRIETARIO.nome());
	}

	@Test
	void grupoPadraoQueNaoEOProprietarioTemPermissoesEditaveis() {
		Grupo gerente = Grupo.padrao(GrupoPadrao.GERENTE);

		gerente.definirPermissoes(EnumSet.allOf(Permissao.class));

		assertThat(gerente.getPermissoes()).containsExactlyInAnyOrder(Permissao.values());
	}

	@Test
	void permissoesDoProprietarioNaoMudam() {
		Grupo proprietario = Grupo.padrao(GrupoPadrao.PROPRIETARIO);

		assertThat(proprietario.ehProprietario()).isTrue();
		assertThatThrownBy(() -> proprietario.definirPermissoes(Set.of()))
				.isInstanceOf(GrupoProprietarioProtegidoException.class);
		assertThat(proprietario.getPermissoes()).containsExactlyInAnyOrder(Permissao.values());
	}

	@Test
	void personalizadoChamadoProprietarioNaoGanhaAProtecao() {
		assertThat(Grupo.personalizado(GrupoPadrao.PROPRIETARIO.nome(), Set.of()).ehProprietario()).isFalse();
	}

	@Test
	void codigosDePermissaoSaoConvertidosEValidados() {
		assertThat(Permissao.dosCodigos(List.of("CLIENTE_CRIAR", "CLIENTE_CRIAR")))
				.containsExactly(Permissao.CLIENTE_CRIAR);
		assertThat(Permissao.codigos(Set.of(Permissao.AUDITORIA_VISUALIZAR, Permissao.CLIENTE_VISUALIZAR)))
				.containsExactly("CLIENTE_VISUALIZAR", "AUDITORIA_VISUALIZAR");
		assertThatThrownBy(() -> Permissao.doCodigo("cliente_criar"))
				.isInstanceOf(ValidacaoException.class).hasMessageContaining("cliente_criar");
		assertThatThrownBy(() -> Permissao.doCodigo(null)).isInstanceOf(ValidacaoException.class);
	}

}
