package br.com.juridora.autorizacao;

import java.util.UUID;

/**
 * Porta que a {@code autorizacao} oferece para saber se um grupo tem usuários (HU09, RN07).
 * <p>
 * Os usuários pertencem à {@code identidade}, que implementa esta interface. Assim a
 * dependência continua no sentido {@code identidade → autorizacao}, sem ciclo.
 */
public interface MembrosDoGrupo {

	/**
	 * {@code true} se algum usuário do escritório da execução, ativo ou desativado, está no
	 * grupo. Desativados contam porque mantêm o grupo e podem ser reativados (RN08).
	 */
	boolean possuiMembros(UUID grupoId);

}
