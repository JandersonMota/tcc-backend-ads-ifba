package br.com.juridora.autorizacao.adaptador.saida.persistencia;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.modelo.NomeDeGrupoJaUsadoException;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Implementação JPA da porta {@link GrupoRepositorio}. */
@Repository
class JpaGrupoRepositorio implements GrupoRepositorio {

	/** Índice único de nome por escritório, sem diferenciar maiúsculas (V8). */
	static final String RESTRICAO_NOME_UNICO = "ux_grupo_tenant_nome";

	private final SpringGrupoRepositorio spring;

	JpaGrupoRepositorio(SpringGrupoRepositorio spring) {
		this.spring = spring;
	}

	@Override
	public Grupo adicionar(Grupo grupo) {
		return gravar(grupo);
	}

	@Override
	public Grupo atualizar(Grupo grupo) {
		return gravar(grupo);
	}

	@Override
	public void remover(Grupo grupo) {
		spring.delete(grupo);
		spring.flush();
	}

	@Override
	public Optional<Grupo> buscar(UUID id) {
		return spring.findById(id);
	}

	@Override
	public Optional<Grupo> buscarParaAlterar(UUID id) {
		return spring.buscarComTravaDeEscrita(id);
	}

	@Override
	public boolean existe(UUID id) {
		return spring.buscarComTravaDeLeitura(id).isPresent();
	}

	@Override
	public List<Grupo> listar() {
		return spring.findAllByOrderByNomeAsc();
	}

	/** Grava já enviando ao banco, para traduzir a violação do nome único aqui. */
	private Grupo gravar(Grupo grupo) {
		try {
			return spring.saveAndFlush(grupo);
		}
		catch (DataIntegrityViolationException excecao) {
			if (violouNomeUnico(excecao)) {
				throw new NomeDeGrupoJaUsadoException();
			}
			throw excecao;
		}
	}

	private static boolean violouNomeUnico(Throwable excecao) {
		for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacao && violacao.getConstraintName() != null
					&& violacao.getConstraintName().toLowerCase(Locale.ROOT).contains(RESTRICAO_NOME_UNICO)) {
				return true;
			}
		}
		return false;
	}

}
