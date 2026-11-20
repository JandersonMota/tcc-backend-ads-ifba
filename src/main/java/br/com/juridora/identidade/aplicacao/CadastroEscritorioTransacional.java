package br.com.juridora.identidade.aplicacao;

import java.util.UUID;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte transacional do cadastro: escritório → grupos padrão → Proprietário, tudo ou
 * nada (decisão D2 do plano da HU05). Chamado só por {@link CadastroEscritorioServico},
 * com o tenant do escritório novo já definido.
 * <p>
 * Com e-mail repetido, a violação de {@code uk_usuario_email} desfaz a transação
 * inteira: não sobra escritório nem grupo.
 */
@Component
class CadastroEscritorioTransacional {

	private final EscritorioApi escritorioApi;
	private final AutorizacaoApi autorizacaoApi;
	private final UsuarioRepositorio usuarios;

	CadastroEscritorioTransacional(EscritorioApi escritorioApi, AutorizacaoApi autorizacaoApi,
			UsuarioRepositorio usuarios) {
		this.escritorioApi = escritorioApi;
		this.autorizacaoApi = autorizacaoApi;
		this.usuarios = usuarios;
	}

	@Transactional
	public ResultadoCadastro registrar(UUID escritorioId, String nomeEscritorio, String nome, Email email,
			String senhaHash) {
		escritorioApi.cadastrar(escritorioId, nomeEscritorio);
		UUID grupoProprietario = autorizacaoApi.criarGruposPadrao();
		Usuario proprietario = usuarios.adicionar(Usuario.proprietario(nome, email, senhaHash, grupoProprietario));
		return new ResultadoCadastro(escritorioId, proprietario.getId());
	}

}
