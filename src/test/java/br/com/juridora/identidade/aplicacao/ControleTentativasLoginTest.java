package br.com.juridora.identidade.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HU06, critério 3 (RNF04): bloqueio temporário após falhas seguidas. */
class ControleTentativasLoginTest {

	private static final String EMAIL = "ana@silva.adv.br";

	private RelogioAjustavel relogio;

	private ControleTentativasLogin controle;

	@BeforeEach
	void preparar() {
		relogio = new RelogioAjustavel(Instant.parse("2026-11-21T12:00:00Z"));
		controle = new ControleTentativasLogin(
				new PropriedadesLogin(5, Duration.ofMinutes(15), Duration.ofMinutes(15)), relogio);
	}

	@Test
	void quatroFalhasAindaDeixamTentar() {
		falhar(4);

		assertThatCode(() -> controle.verificarLiberado(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void aQuintaFalhaBloqueiaPeloTempoConfigurado() {
		falhar(5);

		assertThatThrownBy(() -> controle.verificarLiberado(EMAIL))
				.isInstanceOfSatisfying(MuitasTentativasException.class,
						excecao -> assertThat(excecao.aguardar()).isEqualTo(Duration.ofMinutes(15)));
	}

	@Test
	void oTempoRestanteDiminuiComORelogio() {
		falhar(5);
		relogio.avancar(Duration.ofMinutes(10));

		assertThatThrownBy(() -> controle.verificarLiberado(EMAIL))
				.isInstanceOfSatisfying(MuitasTentativasException.class,
						excecao -> assertThat(excecao.aguardar()).isEqualTo(Duration.ofMinutes(5)));
	}

	@Test
	void terminadoOBloqueioOContadorRecomeca() {
		falhar(5);
		relogio.avancar(Duration.ofMinutes(15));

		assertThatCode(() -> controle.verificarLiberado(EMAIL)).doesNotThrowAnyException();
		falhar(4);
		assertThatCode(() -> controle.verificarLiberado(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void falhasForaDaJanelaNaoSeSomam() {
		falhar(4);
		relogio.avancar(Duration.ofMinutes(15));
		falhar(4);

		assertThatCode(() -> controle.verificarLiberado(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void loginComSucessoZeraOContador() {
		falhar(4);
		controle.registrarSucesso(EMAIL);
		falhar(4);

		assertThatCode(() -> controle.verificarLiberado(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void falhasDeUmEmailNaoBloqueiamOutro() {
		falhar(5);

		assertThatCode(() -> controle.verificarLiberado("bruno@silva.adv.br")).doesNotThrowAnyException();
	}

	@Test
	void maximoDeFalhasPrecisaSerPositivo() {
		assertThatThrownBy(() -> new PropriedadesLogin(0, Duration.ofMinutes(15), Duration.ofMinutes(15)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void falhar(int vezes) {
		for (int i = 0; i < vezes; i++) {
			controle.registrarFalha(EMAIL);
		}
	}

	/** Relógio que o teste avança manualmente. */
	private static final class RelogioAjustavel extends Clock {

		private Instant agora;

		RelogioAjustavel(Instant inicio) {
			this.agora = inicio;
		}

		void avancar(Duration tempo) {
			agora = agora.plus(tempo);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}

	}

}
