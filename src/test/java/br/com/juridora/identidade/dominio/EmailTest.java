package br.com.juridora.identidade.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.juridora.compartilhado.excecao.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

	@Test
	void normalizaParaMinusculasSemEspacos() {
		assertThat(new Email("  Joao.Silva@Exemplo.COM ").valor()).isEqualTo("joao.silva@exemplo.com");
	}

	@Test
	void mesmoEmailComCaixaDiferenteEhIgual() {
		assertThat(new Email("Joao@x.com")).isEqualTo(new Email("joao@x.com"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "joao", "joao@", "@x.com", "joao@x", "jo ao@x.com", "a@b@c.com" })
	void rejeitaEmailInvalido(String valor) {
		assertThatThrownBy(() -> new Email(valor)).isInstanceOf(ValidacaoException.class);
	}

	@Test
	void rejeitaEmailComMaisDe254Caracteres() {
		String local = "a".repeat(64);
		String dominio = "b".repeat(186) + ".com"; // 64 + 1 + 190 = 255
		assertThatThrownBy(() -> new Email(local + "@" + dominio)).isInstanceOf(ValidacaoException.class);
	}

}
