package br.com.juridora.escritorio.aplicacao.consulta;

import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.escritorio.LimitesDoPlano;
import br.com.juridora.escritorio.dominio.modelo.Escritorio;
import br.com.juridora.escritorio.dominio.modelo.Plano;
import br.com.juridora.escritorio.dominio.repositorio.EscritorioRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Limites do plano do escritório da execução (RN09), lidos da configuração. */
@Service
public class ConsultarLimitesDoPlanoCasoDeUso {

	private final EscritorioRepositorio escritorios;

	private final LimitesPorPlano limitesPorPlano;

	private final TenantContext tenantContext;

	ConsultarLimitesDoPlanoCasoDeUso(EscritorioRepositorio escritorios, LimitesPorPlano limitesPorPlano,
			TenantContext tenantContext) {
		this.escritorios = escritorios;
		this.limitesPorPlano = limitesPorPlano;
		this.tenantContext = tenantContext;
	}

	@Transactional(readOnly = true)
	public LimitesDoPlano limitesDoPlano() {
		Plano plano = escritorios.buscar(tenantContext.tenantAtual())
				.map(Escritorio::getPlano)
				.orElseThrow(() -> new IllegalStateException("Escritório da execução não encontrado"));
		return limitesPorPlano.limites(plano);
	}

	/** Porta de configuração: limites de cada plano. */
	public interface LimitesPorPlano {

		LimitesDoPlano limites(Plano plano);

	}

}
