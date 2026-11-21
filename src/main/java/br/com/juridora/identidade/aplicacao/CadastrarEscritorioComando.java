package br.com.juridora.identidade.aplicacao;

/** Dados do cadastro de um escritório com o seu Proprietário (HU05). Sem tenant: ele é gerado. */
public record CadastrarEscritorioComando(String nomeEscritorio, String nome, String email, String senha) {

	@Override
	public String toString() {
		// nunca expor a senha em logs
		return "CadastrarEscritorioComando[nomeEscritorio=" + nomeEscritorio + ", nome=" + nome + "]";
	}

}
