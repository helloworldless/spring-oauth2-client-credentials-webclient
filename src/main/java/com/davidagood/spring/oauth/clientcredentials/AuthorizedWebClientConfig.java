package com.davidagood.spring.oauth.clientcredentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthorizedWebClientConfig {

	public static final String REGISTRATION_ID = "my-client";

	@Bean("authenticatedWebClient")
	WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager,
			OAuth2AuthorizedClientService authorizedClientService, ExchangeStrategies exchangeStrategies) {
		ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(
				authorizedClientManager);
		var failureHandler = new RemoveAuthorizedClientOAuth2AuthorizationFailureHandler(
				(clientRegistrationId, principal, attributes) -> authorizedClientService
						.removeAuthorizedClient(clientRegistrationId, principal.getName()));
		oauth2Client.setAuthorizationFailureHandler(failureHandler);
		// @formatter:off
		return WebClient.builder()
						.apply(oauth2Client.oauth2Configuration())
						.exchangeStrategies(exchangeStrategies)
						.build();
		// @formatter:on
	}

	@Bean
	ExchangeStrategies exchangeStrategies(ObjectMapper objectMapper) {
		Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(objectMapper);
		Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(objectMapper);
		return ExchangeStrategies.builder().codecs(configurer -> {
			configurer.defaultCodecs().jackson2JsonEncoder(encoder);
			configurer.defaultCodecs().jackson2JsonDecoder(decoder);
		}).build();
	}

	/**
	 * Since we do not want the client credentials grant request to be tied to specific
	 * HTTP session via the context of a servlet request, we do not use the
	 * DefaultOAuth2AuthorizedClientManager here. Instead we use the
	 * AuthorizedClientServiceOAuth2AuthorizedClientManager which is designed for use
	 * outside of a servlet context and behaves like we would expect for
	 * service-to-service authorization
	 */
	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
			OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient) {
		OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials(r -> r.accessTokenResponseClient(tokenResponseClient)).clientCredentials().build();
		var authorizedClientServiceOAuth2AuthorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
				clientRegistrationRepository, oAuth2AuthorizedClientService);
		authorizedClientServiceOAuth2AuthorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
		return authorizedClientServiceOAuth2AuthorizedClientManager;
	}

	@Bean
	OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient() {
		return new DefaultClientCredentialsTokenResponseClient();
	}

}
