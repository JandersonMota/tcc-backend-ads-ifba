package br.com.juridora.identidade.adaptador.saida.persistencia;

import java.util.UUID;

import br.com.juridora.autorizacao.MembrosDoGrupo;
import org.springframework.stereotype.Repository;

/**
 * Porta {@link MembrosDoGrupo} da {@code autorizacao}, consultada antes de excluir um grupo
 * (HU09, RN07). Conta usuários ativos e desativados, no tenant da execução, e participa da
 * transação de quem chama.
 */
@Repository
class JpaMembrosDoGrupo implements MembrosDoGrupo {

	private final SpringUsuarioRepositorio usuarios;

	JpaMembrosDoGrupo(SpringUsuarioRepositorio usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	public boolean possuiMembros(UUID grupoId) {
		return usuarios.existsByGrupoId(grupoId);
	}

}
