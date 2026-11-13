package br.com.juridora;

import org.springframework.boot.SpringApplication;

public class TestJuridoraApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(JuridoraApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
