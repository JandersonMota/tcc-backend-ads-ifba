package br.com.juridora.autorizacao.adaptador.saida.persistencia;

import br.com.juridora.autorizacao.dominio.modelo.Grupo;
import br.com.juridora.autorizacao.dominio.repositorio.GrupoRepositorio;
import org.springframework.stereotype.Repository;

/** Implementação JPA da porta {@link GrupoRepositorio}. */
@Repository
class JpaGrupoRepositorio implements GrupoRepositorio {

	private final SpringGrupoRepositorio spring;

	JpaGrupoRepositorio(SpringGrupoRepositorio spring) {
		this.spring = spring;
	}

	@Override
	public Grupo adicionar(Grupo grupo) {
		return spring.save(grupo);
	}

}
