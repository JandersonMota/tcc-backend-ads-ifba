package br.com.juridora.identidade.aplicacao;

import java.util.UUID;

import br.com.juridora.compartilhado.excecao.ValidacaoException;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.identidade.dominio.CodificadorSenha;
import br.com.juridora.identidade.dominio.Email;
import org.springframework.stereotype.Service;

/**
 * Cadastro de escritório com o seu Proprietário (HU05, RF01) – operação pública, sem JWT.
 * <p>
 * Propositalmente <strong>não</strong> transacional (ADR-013): o tenant precisa estar
 * definido antes de abrir a transação, para que {@code @TenantId} e
 * {@code set_config('app.tenant_id')} já valham para o escritório novo. Por isso o ID
 * é gerado aqui, pelo sistema (nunca vem da requisição), e só depois
 * {@link CadastroEscritorioTransacional} grava tudo em uma transação.
 */
@Service
public class CadastroEscritorioServico {

	public static final int TAMANHO_MINIMO_SENHA = 8;
	/** O BCrypt considera só os primeiros 72 bytes; acima disso a senha é rejeitada. */
	public static final int TAMANHO_MAXIMO_SENHA = 72;

	private final TenantContext tenantContext;
	private final CodificadorSenha codificadorSenha;
	private final CadastroEscritorioTransacional transacional;

	CadastroEscritorioServico(TenantContext tenantContext, CodificadorSenha codificadorSenha,
			CadastroEscritorioTransacional transacional) {
		this.tenantContext = tenantContext;
		this.codificadorSenha = codificadorSenha;
		this.transacional = transacional;
	}

	public ResultadoCadastro cadastrar(CadastrarEscritorioComando comando) {
		Email email = new Email(comando.email());
		validarSenha(comando.senha());
		// O hash (lento por definição) é calculado fora da transação, sem segurar conexão.
		String senhaHash = codificadorSenha.codificar(comando.senha());
		UUID escritorioId = UUID.randomUUID();
		return tenantContext.executarComo(escritorioId, null,
				() -> transacional.registrar(escritorioId, comando.nomeEscritorio(), comando.nome(), email, senhaHash));
	}

	private static void validarSenha(String senha) {
		if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
			throw new ValidacaoException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
		}
		if (senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_SENHA) {
			throw new ValidacaoException("A senha deve ter até " + TAMANHO_MAXIMO_SENHA + " bytes.");
		}
	}

}
