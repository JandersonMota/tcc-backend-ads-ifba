package br.com.juridora.identidade.dominio;

/** Porta de saída para gerar e conferir o hash forte da senha (RNF03). */
public interface CodificadorSenha {

	String codificar(String senha);

	boolean confere(String senha, String hash);

}
