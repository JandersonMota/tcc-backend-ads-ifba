package br.com.leija;

import org.springframework.boot.SpringApplication;

public class TestTccBackendAdsIfbaApplication {

	public static void main(String[] args) {
		SpringApplication.from(TccBackendAdsIfbaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
