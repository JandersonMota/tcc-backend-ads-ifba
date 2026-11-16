package br.com.juridora.compartilhado.infraestrutura.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

class TenantContextTest {

	private final TenantContext contexto = new TenantContext();
	private final UUID tenant = UUID.randomUUID();
	private final UUID usuario = UUID.randomUUID();

	@Test
	void foraDeExecutarComoNaoHaTenant() {
		assertThat(contexto.tenantOpcional()).isEmpty();
		assertThat(contexto.usuarioOpcional()).isEmpty();
		assertThatThrownBy(contexto::tenantAtual).isInstanceOf(TenantNaoDefinidoException.class);
	}

	@Test
	void dentroDeExecutarComoExpoeTenantEUsuario() {
		UUID lido = contexto.executarComo(tenant, usuario, contexto::tenantAtual);

		assertThat(lido).isEqualTo(tenant);
		assertThat(contexto.executarComo(tenant, usuario, contexto::usuarioAtual)).isEqualTo(usuario);
	}

	@Test
	void tenantNaoVazaAoTerminarAExecucao() {
		contexto.executarComo(tenant, usuario, () -> { });

		assertThat(contexto.tenantOpcional()).isEmpty();
	}

	@Test
	void tenantNaoVazaParaOutraThread() {
		var emOutraThread = contexto.executarComo(tenant, usuario,
				() -> CompletableFuture.supplyAsync(contexto::tenantOpcional).join());

		assertThat(emOutraThread).isEmpty();
	}

	@Test
	void usuarioEhOpcional() {
		assertThat(contexto.executarComo(tenant, null, contexto::usuarioOpcional)).isEmpty();
	}

	@Test
	void tenantEhObrigatorio() {
		assertThatThrownBy(() -> contexto.executarComo(null, usuario, () -> { }))
				.isInstanceOf(NullPointerException.class);
	}

}
