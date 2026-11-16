package br.com.juridora.compartilhado.infraestrutura.tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import br.com.juridora.compartilhado.dominio.EntidadeTenant;
import org.hibernate.Interceptor;
import org.hibernate.cfg.SessionEventSettings;
import org.hibernate.type.Type;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Preenche as colunas de controle de {@link EntidadeTenant} ({@code created_at},
 * {@code updated_at}, {@code created_by}) ao gravar, sem que o domínio dependa da
 * infraestrutura.
 */
@Component
class ColunasDeControleInterceptor implements Interceptor, HibernatePropertiesCustomizer {

	private final TenantContext tenantContext;
	private final Clock relogio = Clock.systemUTC();

	ColunasDeControleInterceptor(TenantContext tenantContext) {
		this.tenantContext = tenantContext;
	}

	@Override
	public boolean onPersist(Object entidade, Object id, Object[] estado, String[] propriedades, Type[] tipos) {
		if (!(entidade instanceof EntidadeTenant)) {
			return false;
		}
		Instant agora = relogio.instant();
		definir(estado, propriedades, "createdAt", agora);
		definir(estado, propriedades, "updatedAt", agora);
		definir(estado, propriedades, "createdBy", tenantContext.usuarioOpcional().orElse(null));
		return true;
	}

	@Override
	public boolean onFlushDirty(Object entidade, Object id, Object[] estadoAtual, Object[] estadoAnterior,
			String[] propriedades, Type[] tipos) {
		if (!(entidade instanceof EntidadeTenant)) {
			return false;
		}
		definir(estadoAtual, propriedades, "updatedAt", relogio.instant());
		return true;
	}

	private static void definir(Object[] estado, String[] propriedades, String nome, Object valor) {
		for (int i = 0; i < propriedades.length; i++) {
			if (propriedades[i].equals(nome)) {
				estado[i] = valor;
				return;
			}
		}
	}

	@Override
	public void customize(Map<String, Object> propriedadesHibernate) {
		propriedadesHibernate.put(SessionEventSettings.INTERCEPTOR, this);
	}

}
