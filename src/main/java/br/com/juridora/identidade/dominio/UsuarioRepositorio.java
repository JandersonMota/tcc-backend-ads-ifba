package br.com.juridora.identidade.dominio;

import java.util.Optional;
import java.util.UUID;

/** Porta de saída de persistência de usuários (sempre no tenant da execução). */
public interface UsuarioRepositorio {

	/**
	 * Grava um usuário novo imediatamente e o devolve com o ID preenchido.
	 *
	 * @throws EmailJaCadastradoException se o e-mail já existir na plataforma
	 */
	Usuario adicionar(Usuario usuario);

	/** Usuário do escritório da execução; vazio se não existir ou for de outro escritório. */
	Optional<Usuario> buscar(UUID id);

	/**
	 * Credencial de login pelo e-mail, em toda a plataforma e <strong>sem</strong> tenant
	 * (HU06, ADR-015). Única exceção ao RLS: use só para autenticar.
	 */
	Optional<CredencialLogin> credencialPorEmail(Email email);

}
