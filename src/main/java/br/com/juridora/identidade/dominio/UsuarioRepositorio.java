package br.com.juridora.identidade.dominio;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Porta de saída de persistência de usuários (sempre no tenant da execução). */
public interface UsuarioRepositorio {

	/**
	 * Grava um usuário novo imediatamente e o devolve com o ID preenchido.
	 *
	 * @throws EmailJaCadastradoException se o e-mail já existir na plataforma
	 */
	Usuario adicionar(Usuario usuario);

	/**
	 * Grava imediatamente as alterações de um usuário já existente.
	 *
	 * @throws EmailJaCadastradoException se o e-mail novo já existir na plataforma
	 */
	Usuario atualizar(Usuario usuario);

	/** Usuário do escritório da execução; vazio se não existir ou for de outro escritório. */
	Optional<Usuario> buscar(UUID id);

	/** Usuários do escritório da execução, ativos e desativados. */
	Page<Usuario> listar(Pageable pagina);

	/**
	 * Quantidade de usuários ativos do escritório da execução, travando até o fim da
	 * transação quem fizer a mesma contagem no mesmo escritório. Assim duas inclusões
	 * simultâneas não passam juntas do limite do plano (RN09).
	 */
	long contarAtivosComTrava();

	/**
	 * Credencial de login pelo e-mail, em toda a plataforma e <strong>sem</strong> tenant
	 * (HU06, ADR-015). Única exceção ao RLS: use só para autenticar.
	 */
	Optional<CredencialLogin> credencialPorEmail(Email email);

}
