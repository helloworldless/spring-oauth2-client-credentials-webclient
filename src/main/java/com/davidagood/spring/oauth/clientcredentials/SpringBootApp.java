package com.davidagood.spring.oauth.clientcredentials;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.function.Supplier;

@SpringBootApplication
@EnableConfigurationProperties({ SecretWordsClientConfig.class })
public class SpringBootApp {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootApp.class, args);
	}

	@Bean
	Supplier<Instant> timestampSupplier() {
		return Instant::now;
	}

}
