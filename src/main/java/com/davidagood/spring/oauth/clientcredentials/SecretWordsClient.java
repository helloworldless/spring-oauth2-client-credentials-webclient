package com.davidagood.spring.oauth.clientcredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.davidagood.spring.oauth.clientcredentials.AuthorizedWebClientConfig.REGISTRATION_ID;
import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

@Component
public class SecretWordsClient {

	private static final Logger log = LoggerFactory.getLogger(SecretWordsClient.class);

	private final WebClient webClient;

	private final SecretWordsClientConfig config;

	public SecretWordsClient(WebClient webClient, SecretWordsClientConfig config) {
		this.webClient = webClient;
		this.config = config;
	}

	public List<String> getSecretWords() {
		var get = HttpMethod.GET;
		var url = config.getUrl();
		log.info("Making HTTP request method={}, url={}", get, url);
		// @formatter:off
		return webClient.method(get)
				.uri(url)
                .attributes(clientRegistrationId(REGISTRATION_ID))
                .retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .block();
        // @formatter:on
	}

}
