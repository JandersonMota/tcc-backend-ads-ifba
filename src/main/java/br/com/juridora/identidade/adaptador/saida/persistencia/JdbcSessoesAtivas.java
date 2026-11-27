package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import br.com.juridora.compartilhado.infraestrutura.seguranca.SessoesAtivas;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta {@link SessoesAtivas}, consultada pelo filtro de segurança em toda requisição
 * autenticada. Uma única consulta, sob RLS, confere as duas tabelas da identidade:
 * <ul>
 *   <li>{@code usuario}: existe, está ativo e não teve as sessões encerradas depois da
 *       emissão do token (HU08);</li>
 *   <li>{@code token_revogado}: o token não foi encerrado no logout (HU07).</li>
 * </ul>
 * Abre a própria transação, para o {@code app.tenant_id} ser definido.
 */
@Repository
class JdbcSessoesAtivas implements SessoesAtivas {

	private static final String SESSAO_ATIVA = """
			select exists (select 1 from usuario
			               where id = :usuarioId and ativo
			                 and (sessoes_validas_desde is null or sessoes_validas_desde <= :emitidoEm))
			   and not exists (select 1 from token_revogado where jti = :jti)""";

	private final JdbcClient jdbc;

	JdbcSessoesAtivas(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean ativa(UUID usuarioId, UUID jti, Instant emitidoEm) {
		return jdbc.sql(SESSAO_ATIVA)
				.param("usuarioId", usuarioId)
				.param("jti", jti)
				.param("emitidoEm", Timestamp.from(emitidoEm))
				.query(Boolean.class)
				.single();
	}

}
