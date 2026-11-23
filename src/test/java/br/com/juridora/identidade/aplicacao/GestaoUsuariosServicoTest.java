package br.com.juridora.identidade.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import br.com.juridora.autorizacao.AutorizacaoApi;
import br.com.juridora.compartilhado.excecao.AcessoNegadoException;
import br.com.juridora.compartilhado.excecao.ValidacaoException;
import br.com.juridora.compartilhado.infraestrutura.tenant.TenantContext;
import br.com.juridora.escritorio.EscritorioApi;
import br.com.juridora.escritorio.LimitesDoPlano;
import br.com.juridora.identidade.dominio.CodificadorSenha;
import br.com.juridora.identidade.dominio.Email;
import br.com.juridora.identidade.dominio.Usuario;
import br.com.juridora.identidade.dominio.UsuarioNaoEncontradoException;
import br.com.juridora.identidade.dominio.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/** HU08: regras da gestão de usuários, sem banco (a integração fica no {@code GestaoUsuariosIT}). */
class GestaoUsuariosServicoTest {

	private static final UUID ESCRITORIO = UUID.randomUUID();
	private static final UUID GRUPO = UUID.randomUUID();
	private static final int LIMITE = 2;

	private final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
	private final AutorizacaoApi autorizacao = mock(AutorizacaoApi.class);
	private final EscritorioApi escritorio = mock(EscritorioApi.class);
	private final CodificadorSenha codificador = mock(CodificadorSenha.class);
	private final TenantContext tenantContext = new TenantContext();

	private GestaoUsuariosServico gestao;

	private Usuario proprietario;
	private Usuario membro;

	@BeforeEach
	void preparar() {
		gestao = new GestaoUsuariosServico(usuarios, autorizacao, escritorio, codificador, tenantContext,
				TransactionOperations.withoutTransaction(),
				Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
		proprietario = comId(Usuario.proprietario("Ana", new Email("ana@exemplo.com"), "hash", GRUPO));
		membro = comId(Usuario.membro("Bruno", new Email("bruno@exemplo.com"), "hash", GRUPO));
		when(usuarios.buscar(proprietario.getId())).thenReturn(Optional.of(proprietario));
		when(usuarios.buscar(membro.getId())).thenReturn(Optional.of(membro));
		when(usuarios.adicionar(any())).thenAnswer(chamada -> chamada.getArgument(0));
		when(usuarios.atualizar(any())).thenAnswer(chamada -> chamada.getArgument(0));
		when(escritorio.limitesDoPlano()).thenReturn(new LimitesDoPlano(LIMITE, 50, 50));
		when(autorizacao.grupoExiste(GRUPO)).thenReturn(true);
		when(codificador.codificar(any())).thenReturn("hash-novo");
	}

	@Test
	void cadastraMembroAtivoComOGrupoEscolhido() {
		when(usuarios.contarAtivosComTrava()).thenReturn(1L);

		comoProprietario(() -> gestao.cadastrar(comando("carla@exemplo.com", GRUPO)));

		ArgumentCaptor<Usuario> gravado = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarios).adicionar(gravado.capture());
		assertThat(gravado.getValue().isProprietario()).isFalse();
		assertThat(gravado.getValue().isAtivo()).isTrue();
		assertThat(gravado.getValue().getGrupoId()).isEqualTo(GRUPO);
		assertThat(gravado.getValue().getSenhaHash()).isEqualTo("hash-novo");
	}

	@Test
	void noLimiteDoPlanoNaoCadastra() {
		when(usuarios.contarAtivosComTrava()).thenReturn((long) LIMITE);

		assertThatThrownBy(() -> comoProprietario(() -> gestao.cadastrar(comando("carla@exemplo.com", GRUPO))))
				.isInstanceOf(LimiteDeUsuariosAtingidoException.class);
		verify(usuarios, never()).adicionar(any());
	}

	@Test
	void grupoQueNaoEhDoEscritorioNaoCadastra() {
		UUID grupoDeOutroEscritorio = UUID.randomUUID();

		assertThatThrownBy(() -> comoProprietario(
				() -> gestao.cadastrar(comando("carla@exemplo.com", grupoDeOutroEscritorio))))
				.isInstanceOf(ValidacaoException.class)
				.hasMessageContaining("grupo");
		verify(usuarios, never()).adicionar(any());
	}

	@Test
	void senhaCurtaFalhaAntesDeCalcularOHash() {
		CadastrarUsuarioComando comando = new CadastrarUsuarioComando("Carla", "carla@exemplo.com", "curta", GRUPO);

		assertThatThrownBy(() -> comoProprietario(() -> gestao.cadastrar(comando)))
				.isInstanceOf(ValidacaoException.class);
		verifyNoInteractions(codificador);
	}

	@Test
	void usuarioDeOutroEscritorioNaoEhEncontrado() {
		UUID deOutroEscritorio = UUID.randomUUID();
		when(usuarios.buscar(deOutroEscritorio)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> comoProprietario(() -> gestao.buscar(deOutroEscritorio)))
				.isInstanceOf(UsuarioNaoEncontradoException.class);
	}

	@Test
	void soOProprietarioAlteraOProprioCadastro() {
		AlterarUsuarioComando comando = new AlterarUsuarioComando("Outro Nome", "ana@exemplo.com");

		assertThatThrownBy(() -> como(membro, () -> gestao.alterar(proprietario.getId(), comando)))
				.isInstanceOf(AcessoNegadoException.class);

		DadosUsuario alterado = comoProprietario(() -> gestao.alterar(proprietario.getId(), comando));
		assertThat(alterado.nome()).isEqualTo("Outro Nome");
	}

	@Test
	void ninguemDesativaASiMesmo() {
		assertThatThrownBy(() -> como(membro, () -> {
			gestao.desativar(membro.getId());
			return null;
		})).isInstanceOf(AutoDesativacaoException.class);
		assertThat(membro.isAtivo()).isTrue();
	}

	@Test
	void reativarUsuarioAtivoNaoConsultaOLimite() {
		comoProprietario(() -> {
			gestao.reativar(membro.getId());
			return null;
		});

		verify(usuarios, never()).contarAtivosComTrava();
	}

	@Test
	void noLimiteDoPlanoNaoReativa() {
		membro.desativar(Instant.parse("2026-11-22T09:00:00Z"));
		when(usuarios.contarAtivosComTrava()).thenReturn((long) LIMITE);

		assertThatThrownBy(() -> comoProprietario(() -> {
			gestao.reativar(membro.getId());
			return null;
		})).isInstanceOf(LimiteDeUsuariosAtingidoException.class);
		assertThat(membro.isAtivo()).isFalse();
	}

	private static CadastrarUsuarioComando comando(String email, UUID grupo) {
		return new CadastrarUsuarioComando("Carla", email, "senha-forte-123", grupo);
	}

	private <T> T comoProprietario(java.util.function.Supplier<T> acao) {
		return como(proprietario, acao);
	}

	private <T> T como(Usuario usuario, java.util.function.Supplier<T> acao) {
		return tenantContext.executarComo(ESCRITORIO, usuario.getId(), acao);
	}

	private static Usuario comId(Usuario usuario) {
		ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
		return usuario;
	}

}
