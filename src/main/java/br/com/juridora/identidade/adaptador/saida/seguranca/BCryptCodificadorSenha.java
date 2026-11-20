package br.com.juridora.identidade.adaptador.saida.seguranca;

import java.util.Map;

import br.com.juridora.identidade.dominio.CodificadorSenha;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hash de senha com BCrypt (RNF03), via {@code spring-security-crypto} apenas: não liga
 * a autoconfiguração do Spring Security nem define o emissor do JWT.
 * <p>
 * O {@link DelegatingPasswordEncoder} grava o prefixo {@code {bcrypt}}, o que permite
 * trocar de algoritmo (ex.: Argon2) no futuro sem invalidar as senhas atuais.
 */
@Component
class BCryptCodificadorSenha implements CodificadorSenha {

	private static final String BCRYPT = "bcrypt";

	private final PasswordEncoder codificador =
			new DelegatingPasswordEncoder(BCRYPT, Map.of(BCRYPT, new BCryptPasswordEncoder()));

	@Override
	public String codificar(String senha) {
		return codificador.encode(senha);
	}

	@Override
	public boolean confere(String senha, String hash) {
		return codificador.matches(senha, hash);
	}

}
