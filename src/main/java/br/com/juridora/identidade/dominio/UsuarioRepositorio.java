package br.com.juridora.identidade.dominio;

/** Porta de saída de persistência de usuários (sempre no tenant da execução). */
public interface UsuarioRepositorio {

	/**
	 * Grava um usuário novo imediatamente e o devolve com o ID preenchido.
	 *
	 * @throws EmailJaCadastradoException se o e-mail já existir na plataforma
	 */
	Usuario adicionar(Usuario usuario);

}
