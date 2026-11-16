package br.com.juridora.isolamento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RegistroIsolamentoRepository extends JpaRepository<RegistroIsolamento, UUID> {

	/** Consulta nativa: o filtro do Hibernate não se aplica; só o RLS protege. */
	@Query(value = "SELECT * FROM registro_isolamento", nativeQuery = true)
	List<RegistroIsolamento> todosPorConsultaNativa();

}
