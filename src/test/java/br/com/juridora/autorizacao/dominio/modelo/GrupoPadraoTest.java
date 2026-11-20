package br.com.juridora.autorizacao.dominio.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HU05, critério 3 – grupos padrão conforme a Matriz de Permissões, §4. */
class GrupoPadraoTest {

	@Test
	void existemOsCincoGruposPadrao() {
		assertThat(Arrays.stream(GrupoPadrao.values()).map(GrupoPadrao::nome))
				.containsExactly("Proprietário", "Gerente", "Advogado", "Estagiário", "Usuário");
	}

	@Test
	void proprietarioTemOCatalogoCompleto() {
		assertThat(GrupoPadrao.PROPRIETARIO.permissoes()).containsExactlyInAnyOrder(Permissao.values());
	}

	@ParameterizedTest
	@CsvSource({ "GERENTE, 19", "ADVOGADO, 12", "ESTAGIARIO, 8", "USUARIO, 6" })
	void quantidadeDePermissoesConfereComAMatriz(GrupoPadrao grupo, int quantidade) {
		assertThat(grupo.permissoes()).hasSize(quantidade);
	}

	@Test
	void somenteOProprietarioTemPermissoesExclusivasDele() {
		for (GrupoPadrao grupo : GrupoPadrao.values()) {
			if (grupo != GrupoPadrao.PROPRIETARIO) {
				assertThat(grupo.permissoes()).doesNotContain(
						Permissao.USUARIO_GERENCIAR, Permissao.AUDITORIA_VISUALIZAR, Permissao.FINANCEIRO_EXCLUIR);
			}
		}
	}

	@Test
	void distribuicaoSegueAMatriz() {
		assertThat(GrupoPadrao.GERENTE.permissoes())
				.contains(Permissao.FINANCEIRO_PAGAMENTO_REGISTRAR, Permissao.PROCESSO_EXCLUIR);
		assertThat(GrupoPadrao.ADVOGADO.permissoes())
				.contains(Permissao.ATIVIDADE_DELEGAR)
				.doesNotContain(Permissao.CLIENTE_EXCLUIR, Permissao.FINANCEIRO_VISUALIZAR);
		assertThat(GrupoPadrao.ESTAGIARIO.permissoes())
				.contains(Permissao.PROCESSO_EDITAR)
				.doesNotContain(Permissao.PROCESSO_CRIAR, Permissao.CLIENTE_CRIAR);
		assertThat(GrupoPadrao.USUARIO.permissoes())
				.contains(Permissao.ATIVIDADE_EDITAR)
				.doesNotContain(Permissao.ATIVIDADE_CRIAR);
		for (GrupoPadrao grupo : GrupoPadrao.values()) {
			assertThat(grupo.permissoes()).contains(
					Permissao.CLIENTE_VISUALIZAR, Permissao.PROCESSO_VISUALIZAR, Permissao.ATIVIDADE_VISUALIZAR,
					Permissao.USUARIO_VISUALIZAR, Permissao.DASHBOARD_VISUALIZAR);
		}
	}

	@Test
	void grupoCriadoAPartirDaDefinicaoEhPadraoComAsMesmasPermissoes() {
		Grupo grupo = Grupo.padrao(GrupoPadrao.ADVOGADO);

		assertThat(grupo.getNome()).isEqualTo("Advogado");
		assertThat(grupo.isPadrao()).isTrue();
		assertThat(grupo.getPermissoes()).isEqualTo(GrupoPadrao.ADVOGADO.permissoes());
	}

}
