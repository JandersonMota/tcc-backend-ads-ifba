package br.com.juridora.escritorio.dominio.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.excecao.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EscritorioTest {

	@Test
	void escritorioNovoNasceNoPlanoFreeEAtivo() {
		UUID id = UUID.randomUUID();
		Instant agora = Instant.parse("2026-11-18T12:00:00Z");

		Escritorio escritorio = Escritorio.cadastrar(id, new NomeEscritorio("  Silva Advogados "), agora);

		assertThat(escritorio.getId()).isEqualTo(id);
		assertThat(escritorio.getNome()).isEqualTo("Silva Advogados");
		assertThat(escritorio.getPlano()).isEqualTo(Plano.FREE);
		assertThat(escritorio.isAtivo()).isTrue();
		assertThat(escritorio.getCreatedAt()).isEqualTo(agora);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void nomeDoEscritorioEhObrigatorio(String nome) {
		assertThatThrownBy(() -> new NomeEscritorio(nome)).isInstanceOf(ValidacaoException.class);
	}

	@Test
	void nomeDoEscritorioTemAte200Caracteres() {
		assertThat(new NomeEscritorio("a".repeat(200)).valor()).hasSize(200);
		assertThatThrownBy(() -> new NomeEscritorio("a".repeat(201))).isInstanceOf(ValidacaoException.class);
	}

}
