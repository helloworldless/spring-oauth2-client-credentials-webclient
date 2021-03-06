package com.davidagood.spring.oauth.clientcredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api")
public class Controller {

	private static final Logger log = LoggerFactory.getLogger(Controller.class);

	private final SecretWordsClient secretWordsClient;

	private final Supplier<Instant> timestampSupplier;

	public Controller(SecretWordsClient secretWordsClient, Supplier<Instant> timestampSupplier) {
		this.secretWordsClient = secretWordsClient;
		this.timestampSupplier = timestampSupplier;
	}

	@GetMapping("/words")
	public SecretWordsDto getSecretWords() {
		log.info("Getting secret words");
		try {
			return SecretWordsDto.from(secretWordsClient.getSecretWords(), timestampSupplier.get());
		}
		catch (AuthorizationException | SecretWordsRequestException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

}
