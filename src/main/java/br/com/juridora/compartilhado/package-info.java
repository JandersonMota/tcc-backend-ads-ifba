/**
 * Tipos base, exceções e infraestrutura comum usados por todos os módulos.
 * <p>
 * Módulo aberto (shared kernel): seus subpacotes ({@code dominio}, {@code excecao},
 * {@code infraestrutura}) podem ser usados pelos demais módulos. Não deve depender de
 * nenhum módulo de negócio.
 */
@ApplicationModule(displayName = "Compartilhado", type = ApplicationModule.Type.OPEN)
package br.com.juridora.compartilhado;

import org.springframework.modulith.ApplicationModule;
