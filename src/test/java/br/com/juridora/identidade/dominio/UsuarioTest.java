package br.com.juridora.identidade.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.excecao.ValidacaoException;
import org.junit.jupiter.api.Test;

/** Regras do usuário na gestão da equipe (HU08). */
class UsuarioTest {

	private static final UUID GRUPO = UUID.randomUUID();

	private static Usuario membro() {
		return Usuario.membro("Bruno", new Email("bruno@exemplo.com"), "hash", GRUPO);
	}

	@Test
	void membroNasceAtivoESemSerProprietario() {
		Usuario usuario = membro();

		assertThat(usuario.isAtivo()).isTrue();
		assertThat(usuario.isProprietario()).isFalse();
		assertThat(usuario.getGrupoId()).isEqualTo(GRUPO);
		assertThat(usuario.getSessoesValidasDesde()).isNull();
	}

	@Test
	void proprietarioNaoPodeSerDesativado() {
		Usuario proprietario = Usuario.proprietario("Ana", new Email("ana@exemplo.com"), "hash", GRUPO);

		assertThatThrownBy(() -> proprietario.desativar(Instant.now()))
				.isInstanceOf(ProprietarioNaoDesativavelException.class);
		assertThat(proprietario.isAtivo()).isTrue();
	}

	@Test
	void desativarMarcaOInstanteNaPrecisaoDoIat() {
		Usuario usuario = membro();

		usuario.desativar(Instant.parse("2026-11-22T10:00:00.700Z"));

		assertThat(usuario.isAtivo()).isFalse();
		assertThat(usuario.getSessoesValidasDesde()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
	}

	@Test
	void desativarDeNovoMantemOInstanteDaPrimeiraVez() {
		Usuario usuario = membro();
		usuario.desativar(Instant.parse("2026-11-22T10:00:00Z"));

		usuario.desativar(Instant.parse("2026-11-22T11:00:00Z"));

		assertThat(usuario.getSessoesValidasDesde()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
	}

	@Test
	void reativarNaoDevolveAsSessoesAntigas() {
		Usuario usuario = membro();
		usuario.desativar(Instant.parse("2026-11-22T10:00:00Z"));

		usuario.reativar();

		assertThat(usuario.isAtivo()).isTrue();
		assertThat(usuario.getSessoesValidasDesde()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
	}

	@Test
	void alterarTrocaNomeEEmailEValidaONome() {
		Usuario usuario = membro();

		usuario.alterar("  Bruno Costa ", new Email("Bruno.Costa@Exemplo.com"));

		assertThat(usuario.getNome()).isEqualTo("Bruno Costa");
		assertThat(usuario.getEmail().valor()).isEqualTo("bruno.costa@exemplo.com");
		assertThatThrownBy(() -> usuario.alterar(" ", new Email("b@exemplo.com")))
				.isInstanceOf(ValidacaoException.class);
	}

	// ------------------------------------------------------------------ HU10

	@Test
	void membroTrocaDeGrupo() {
		Usuario usuario = membro();
		UUID outro = UUID.randomUUID();

		assertThat(usuario.trocarGrupo(outro)).isTrue();
		assertThat(usuario.getGrupoId()).isEqualTo(outro);
	}

	@Test
	void trocarParaOMesmoGrupoNaoMudaNada() {
		Usuario usuario = membro();

		assertThat(usuario.trocarGrupo(GRUPO)).isFalse();
		assertThat(usuario.getGrupoId()).isEqualTo(GRUPO);
	}

	@Test
	void proprietarioNaoSaiDoGrupoProprietario() {
		Usuario proprietario = Usuario.proprietario("Ana", new Email("ana@exemplo.com"), "hash", GRUPO);

		assertThatThrownBy(() -> proprietario.trocarGrupo(UUID.randomUUID()))
				.isInstanceOf(GrupoReservadoAoProprietarioException.class);
		assertThat(proprietario.getGrupoId()).isEqualTo(GRUPO);
	}

}
