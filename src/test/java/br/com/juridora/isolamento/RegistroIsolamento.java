package br.com.juridora.isolamento;

import br.com.juridora.compartilhado.dominio.EntidadeTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Entidade usada somente nos testes de isolamento (tabela {@code registro_isolamento}). */
@Entity
@Table(name = "registro_isolamento")
class RegistroIsolamento extends EntidadeTenant {

	@Column(nullable = false, length = 200)
	private String descricao;

	protected RegistroIsolamento() {
	}

	RegistroIsolamento(String descricao) {
		this.descricao = descricao;
	}

	String getDescricao() {
		return descricao;
	}

	void renomear(String novaDescricao) {
		this.descricao = novaDescricao;
	}

}
