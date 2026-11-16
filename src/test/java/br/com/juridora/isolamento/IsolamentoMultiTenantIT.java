package br.com.juridora.isolamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import br.com.juridora.TestcontainersConfiguration;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU03 – isolamento multi-tenant nas duas camadas (ADR-002 e ADR-003), com
 * PostgreSQL real e a aplicação conectada como {@code app_user} (sem {@code BYPASSRLS}).
 * <p>
 * Camada 1 (aplicação): Hibernate {@code @TenantId}. Camada 2 (banco): RLS com
 * {@code app.tenant_id} definido no início de cada transação.
 * <p>
 * O pool tem uma única conexão para provar que o tenant de uma transação não vaza
 * para a próxima que reutiliza a mesma conexão.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@Import(TestcontainersConfiguration.class)
class IsolamentoMultiTenantIT {

	@Autowired
	TenantContext tenantContext;

	@Autowired
	RegistroIsolamentoRepository repositorio;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transacao;

	@Autowired
	PostgreSQLContainer postgres;

	UUID escritorioA;
	UUID escritorioB;
	UUID joao;
	UUID carlos;
	UUID registroA;
	UUID registroB;

	@BeforeEach
	void criarDoisEscritoriosComDados() {
		escritorioA = criarEscritorio("Escritório A");
		escritorioB = criarEscritorio("Escritório B");
		joao = UUID.randomUUID();
		carlos = UUID.randomUUID();
		registroA = comoA(() -> repositorio.save(new RegistroIsolamento("Processo A")).getId());
		registroB = comoB(() -> repositorio.save(new RegistroIsolamento("Processo B")).getId());
	}

	@Nested
	class CamadaAplicacao {

		@Test
		void usuarioDoEscritorioASoRecebeDadosDeA() {
			List<RegistroIsolamento> registros = comoA(() -> repositorio.findAll());

			assertThat(registros).extracting(RegistroIsolamento::getTenantId).containsOnly(escritorioA);
			assertThat(registros).extracting(RegistroIsolamento::getId).contains(registroA).doesNotContain(registroB);
		}

		@Test
		void registroDeOutroEscritorioEhTratadoComoInexistente() {
			assertThat(comoA(() -> repositorio.findById(registroB))).isEmpty();
			assertThat(comoA(() -> repositorio.existsById(registroB))).isFalse();
		}

		@Test
		void naoExcluiNemAlteraRegistroDeOutroEscritorio() {
			executarComoA(() -> repositorio.deleteById(registroB));
			executarComoA(() -> transacao.executeWithoutResult(status ->
					repositorio.findById(registroB).ifPresent(r -> r.renomear("adulterado"))));

			RegistroIsolamento registro = comoB(() -> repositorio.findById(registroB)).orElseThrow();
			assertThat(registro.getDescricao()).isEqualTo("Processo B");
		}

		@Test
		void insercaoRecebeTenantEColunasDeControleDaExecucao() {
			RegistroIsolamento registro = comoA(() -> repositorio.findById(registroA)).orElseThrow();

			assertThat(registro.getTenantId()).isEqualTo(escritorioA);
			assertThat(registro.getCreatedBy()).isEqualTo(joao);
			assertThat(registro.getCreatedAt()).isNotNull();
			assertThat(registro.getUpdatedAt()).isNotNull();
		}

	}

	@Nested
	class CamadaAplicacaoSozinha {

		/** Com o RLS desligado, só o filtro {@code @TenantId} do Hibernate protege. */
		@Test
		void filtroDoHibernateIsolaMesmoSemRls() throws SQLException {
			definirRls(false);
			try {
				List<RegistroIsolamento> registros = comoA(() -> repositorio.findAll());
				assertThat(registros).extracting(RegistroIsolamento::getTenantId).containsOnly(escritorioA);
				assertThat(comoA(() -> repositorio.findById(registroB))).isEmpty();

				// prova de que o RLS estava mesmo desligado: SQL sem filtro enxerga os dois escritórios
				List<UUID> semFiltro = comoA(() -> transacao.execute(status ->
						jdbc.queryForList("SELECT DISTINCT tenant_id FROM registro_isolamento", UUID.class)));
				assertThat(semFiltro).contains(escritorioA, escritorioB);
			}
			finally {
				definirRls(true);
			}
		}

		private void definirRls(boolean ligado) throws SQLException {
			String comando = ligado
					? "ALTER TABLE registro_isolamento ENABLE ROW LEVEL SECURITY; ALTER TABLE registro_isolamento FORCE ROW LEVEL SECURITY"
					: "ALTER TABLE registro_isolamento NO FORCE ROW LEVEL SECURITY; ALTER TABLE registro_isolamento DISABLE ROW LEVEL SECURITY";
			try (Connection dono = DriverManager.getConnection(postgres.getJdbcUrl(), "app_owner", "app_owner");
					Statement sql = dono.createStatement()) {
				sql.execute(comando);
			}
		}

	}

	@Nested
	class CamadaBanco {

		@Test
		void consultaNativaSemWhereTenantSoRetornaLinhasDoTenant() {
			// como nos casos de uso (@Transactional): é no início da transação que app.tenant_id é definido
			List<RegistroIsolamento> nativos = comoA(() -> transacao.execute(status -> repositorio.todosPorConsultaNativa()));
			assertThat(nativos).isNotEmpty().extracting(RegistroIsolamento::getTenantId).containsOnly(escritorioA);

			List<UUID> tenants = comoA(() -> transacao.execute(status ->
					jdbc.queryForList("SELECT tenant_id FROM registro_isolamento", UUID.class)));
			assertThat(tenants).isNotEmpty().containsOnly(escritorioA);
		}

		@Test
		void insercaoComTenantDeOutroEscritorioEhRejeitada() {
			assertThatThrownBy(() -> executarComoA(() -> transacao.executeWithoutResult(status -> jdbc.update(
					"INSERT INTO registro_isolamento (id, tenant_id, descricao, created_at, updated_at) "
							+ "VALUES (?, ?, 'forjado', now(), now())",
					UUID.randomUUID(), escritorioB))))
					.isInstanceOf(DataAccessException.class)
					.rootCause().hasMessageContaining("violates row-level security policy");
		}

		@Test
		void updateEDeleteSemWhereTenantNaoAtingemOutroEscritorio() {
			int alterados = comoA(() -> transacao.execute(status ->
					jdbc.update("UPDATE registro_isolamento SET descricao = 'adulterado'")));
			int excluidos = comoA(() -> transacao.execute(status ->
					jdbc.update("DELETE FROM registro_isolamento WHERE id = ?", registroB)));

			assertThat(alterados).isPositive();
			assertThat(excluidos).isZero();
			assertThat(comoB(() -> repositorio.findById(registroB)))
					.get().extracting(RegistroIsolamento::getDescricao).isEqualTo("Processo B");
		}

	}

	@Nested
	class SemTenant {

		@Test
		void consultasNaoRetornamNenhumaLinha() {
			assertThat(repositorio.findAll()).isEmpty();
			List<RegistroIsolamento> nativos = transacao.execute(status -> repositorio.todosPorConsultaNativa());
			assertThat(nativos).isEmpty();
			Long semTransacao = jdbc.queryForObject("SELECT count(*) FROM registro_isolamento", Long.class);
			Long emTransacao = transacao.execute(status ->
					jdbc.queryForObject("SELECT count(*) FROM registro_isolamento", Long.class));
			assertThat(semTransacao).isZero();
			assertThat(emTransacao).isZero();
		}

		@Test
		void insercaoEhRejeitada() {
			assertThatThrownBy(() -> repositorio.save(new RegistroIsolamento("sem tenant")))
					.isInstanceOf(DataAccessException.class);
		}

		@Test
		void tenantDaTransacaoAnteriorNaoVazaParaAProxima() {
			List<RegistroIsolamento> deA = comoA(() -> repositorio.findAll());
			assertThat(deA).isNotEmpty();

			// mesma (e única) conexão do pool, agora sem tenant
			Long semTenant = transacao.execute(status ->
					jdbc.queryForObject("SELECT count(*) FROM registro_isolamento", Long.class));
			assertThat(semTenant).isZero();
			assertThat(jdbc.queryForObject("SELECT current_setting('app.tenant_id', true)", String.class))
					.isNullOrEmpty();
		}

	}

	@Test
	void tenantNaoPodeSerDefinidoComTransacaoJaAberta() {
		assertThatThrownBy(() -> transacao.executeWithoutResult(status ->
				tenantContext.executarComo(escritorioA, joao, () -> repositorio.findAll())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("antes de abrir a transação");
	}

	// ----------------------------------------------------------------- apoio

	private UUID criarEscritorio(String nome) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO escritorio (id, nome) VALUES (?, ?)", id, nome);
		return id;
	}

	private <T> T comoA(java.util.function.Supplier<T> acao) {
		return tenantContext.executarComo(escritorioA, joao, acao);
	}

	private void executarComoA(Runnable acao) {
		tenantContext.executarComo(escritorioA, joao, acao);
	}

	private <T> T comoB(java.util.function.Supplier<T> acao) {
		return tenantContext.executarComo(escritorioB, carlos, acao);
	}

}
