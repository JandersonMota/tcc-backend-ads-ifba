package br.com.juridora.identidade.aplicacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Bloqueio temporário de login por e-mail (HU06, critério 3, RNF04; decisão D4 do plano).
 * <p>
 * Conta as falhas de cada e-mail, inclusive de e-mails que não existem, para que o
 * bloqueio não revele se o e-mail está cadastrado. Com {@code maximoFalhas} falhas dentro
 * da janela, o e-mail fica bloqueado por {@code bloqueio}; um login bem-sucedido zera o
 * contador.
 * <p>
 * Fica em memória: zera quando a aplicação reinicia e não é compartilhado entre
 * instâncias (o MVP roda em uma só).
 */
@Component
public class ControleTentativasLogin {

	/** Acima disso, os registros vencidos são descartados (evita crescer sem limite). */
	static final int LIMITE_PARA_LIMPEZA = 10_000;

	private final PropriedadesLogin propriedades;

	private final Clock relogio;

	private final ConcurrentMap<String, Registro> registros = new ConcurrentHashMap<>();

	public ControleTentativasLogin(PropriedadesLogin propriedades, Clock relogio) {
		this.propriedades = propriedades;
		this.relogio = relogio;
	}

	/** Falhas somadas de um e-mail; {@code bloqueadoAte} só existe depois de atingir o máximo. */
	private record Registro(int falhas, Instant inicioJanela, Instant bloqueadoAte) {
	}

	/**
	 * @throws MuitasTentativasException se o e-mail estiver bloqueado
	 */
	public void verificarLiberado(String chave) {
		Instant agora = relogio.instant();
		Registro registro = registros.get(chave);
		if (registro != null && registro.bloqueadoAte() != null && agora.isBefore(registro.bloqueadoAte())) {
			throw new MuitasTentativasException(Duration.between(agora, registro.bloqueadoAte()));
		}
	}

	public void registrarFalha(String chave) {
		Instant agora = relogio.instant();
		registros.compute(chave, (_, atual) -> {
			if (atual == null || venceu(atual, agora)) {
				return novo(1, agora, agora);
			}
			return novo(atual.falhas() + 1, atual.inicioJanela(), agora);
		});
		if (registros.size() > LIMITE_PARA_LIMPEZA) {
			registros.values().removeIf(registro -> venceu(registro, agora));
		}
	}

	public void registrarSucesso(String chave) {
		registros.remove(chave);
	}

	private Registro novo(int falhas, Instant inicioJanela, Instant agora) {
		Instant bloqueadoAte = falhas >= propriedades.maximoFalhas() ? agora.plus(propriedades.bloqueio()) : null;
		return new Registro(falhas, inicioJanela, bloqueadoAte);
	}

	private boolean venceu(Registro registro, Instant agora) {
		if (registro.bloqueadoAte() != null) {
			return !agora.isBefore(registro.bloqueadoAte());
		}
		return !agora.isBefore(registro.inicioJanela().plus(propriedades.janela()));
	}

}
