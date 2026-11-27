package br.com.juridora.escritorio.configuracao;

import java.util.Objects;

import br.com.juridora.escritorio.LimitesDoPlano;
import br.com.juridora.escritorio.aplicacao.consulta.ConsultarLimitesDoPlanoCasoDeUso.LimitesPorPlano;
import br.com.juridora.escritorio.dominio.modelo.Plano;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de cada plano (RN09: "os limites vêm de configuração"), em
 * {@code juridora.planos}. No MVP só existe o Free.
 */
@ConfigurationProperties("juridora.planos")
record PropriedadesPlanos(LimitesDoPlano free) implements LimitesPorPlano {

	PropriedadesPlanos {
		Objects.requireNonNull(free, "juridora.planos.free é obrigatório");
	}

	@Override
	public LimitesDoPlano limites(Plano plano) {
		return switch (plano) {
			case FREE -> free;
		};
	}

}
