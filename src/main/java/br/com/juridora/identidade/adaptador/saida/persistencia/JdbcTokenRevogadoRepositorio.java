package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import br.com.juridora.identidade.dominio.TokenRevogadoRepositorio;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabela {@code token_revogado} (V6) via JDBC: é um registro técnico de vida curta, sem
 * comportamento de domínio, então não vira entidade JPA.
 * <p>
 * {@code tenant_id} não é informado: vem do tenant da transação ({@code DEFAULT} da coluna),
 * e o RLS valida. A consulta e a gravação abrem a própria transação, para o
 * {@code app.tenant_id} ser definido. A limpeza roda sem tenant, pela função
 * {@code remover_tokens_revogados_vencidos} ({@code SECURITY DEFINER}).
 * <p>
 * A consulta feita a cada requisição fica em {@link JdbcSessoesAtivas}.
 */
@Repository
class JdbcTokenRevogadoRepositorio implements TokenRevogadoRepositorio {

	private static final String REVOGAR = """
			insert into token_revogado (jti, usuario_id, expira_em)
			values (:jti, :usuarioId, :expiraEm)
			on conflict (jti) do nothing""";

	private static final String REMOVER_VENCIDOS = "select remover_tokens_revogados_vencidos()";

	private final JdbcClient jdbc;

	JdbcTokenRevogadoRepositorio(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void revogar(UUID jti, UUID usuarioId, Instant expiraEm) {
		jdbc.sql(REVOGAR)
				.param("jti", jti)
				.param("usuarioId", usuarioId)
				.param("expiraEm", Timestamp.from(expiraEm))
				.update();
	}

	@Override
	public int removerVencidos() {
		return jdbc.sql(REMOVER_VENCIDOS).query(Integer.class).single();
	}

}
