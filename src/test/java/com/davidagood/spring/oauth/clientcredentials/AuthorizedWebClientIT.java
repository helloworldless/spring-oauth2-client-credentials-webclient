package com.davidagood.spring.oauth.clientcredentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.OAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.davidagood.spring.oauth.clientcredentials.AuthorizedWebClientConfig.REGISTRATION_ID;
import static com.davidagood.spring.oauth.clientcredentials.TestUtil.getFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizedWebClientIT {

	static final String DUMMY_ACCESS_TOKEN = "dummy-access-token";

	private static final int MOCK_SERVER_PORT = getFreePort();

	private static final Instant FIXED_TIMESTAMP = LocalDate.of(2020, 1, 8).atStartOfDay()
			.atZone(ZoneId.of("America/New_York")).toInstant();

	private static MockWebServer mockWebServer;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	@Qualifier("oauth2RestTemplate")
	RestTemplate oauth2RestTemplate;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ClientRegistrationRepository clientRegistrationRepository;

	@SpyBean
	@Qualifier("authorizationServerAuthorizationSuccessHandler")
	OAuth2AuthorizationSuccessHandler authorizationServerAuthorizationSuccessHandler;

	@SpyBean
	@Qualifier("authorizationServerAuthorizationFailureHandler")
	OAuth2AuthorizationFailureHandler authorizationServerAuthorizationFailureHandler;

	@SpyBean
	@Qualifier("resourceServerAuthorizationFailureHandler")
	OAuth2AuthorizationFailureHandler resourceServerAuthorizationFailureHandler;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) {
		r.add("secret-words-client.url", () -> "http://localhost:" + MOCK_SERVER_PORT);
		r.add("spring.security.oauth2.client.provider.my-client-provider.token-uri",
				() -> "http://localhost:" + MOCK_SERVER_PORT);
	}

	@BeforeEach
	void setUp() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start(MOCK_SERVER_PORT);
	}

	@AfterEach
	void tearDown() throws IOException {
		mockWebServer.close();
	}

	/*
	 * By running this test twice, we make sure the that
	 * InMemoryOAuth2AuthorizedClientService's authorizedClient from one test is not
	 * carried over in Spring context to a subsequent test. Notice that this class is
	 * annotated @DirtiesContext, without which the authorizedClients are carried over in
	 * the Spring context. We could manually delete the authorizedClient after each test
	 * using InMemoryOAuth2AuthorizedClientService#removeAuthorizedClient, but just
	 * declaring @DirtiesContext is better since it doesn't involve fiddling with internal
	 * Spring state
	 */
	@RepeatedTest(2)
	void happyPath() throws Exception {
		var secretWords = List.of("speakers", "keyboard");
		var expected = SecretWordsDto.from(secretWords, FIXED_TIMESTAMP);

		mockWebServer.enqueue(createAuthServerGrantRequestSuccessResponse());
		mockWebServer.enqueue(createResourceServerSuccessResponse(secretWords));
		mockWebServer.enqueue(createResourceServerSuccessResponse(secretWords));

		mockMvc.perform(get("/api/words")).andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(expected), true));

		mockMvc.perform(get("/api/words")).andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(expected), true));

		verify(authorizationServerAuthorizationSuccessHandler, times(1)).onAuthorizationSuccess(any(), any(), any());

		RecordedRequest authServerRequest = mockWebServer.takeRequest();
		assertThat(authServerRequest.getRequestUrl()).isEqualTo(HttpUrl.parse(
				clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID).getProviderDetails().getTokenUri()));
		assertThat(authServerRequest.getHeader(HttpHeaders.CONTENT_TYPE))
				.isEqualTo(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8).toString());

		RecordedRequest firstResourceServerRequest = mockWebServer.takeRequest();
		assertThat(firstResourceServerRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));

		RecordedRequest secondResourceServerRequest = mockWebServer.takeRequest();
		assertThat(secondResourceServerRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));
	}

	@Test
	void resourceServerUnauthorizedAuthorizedClientRemovalTest() throws Exception {
		var secretWords = List.of("speakers", "keyboard");
		var expected = SecretWordsDto.from(secretWords, FIXED_TIMESTAMP);

		mockWebServer.enqueue(createAuthServerGrantRequestSuccessResponse());
		mockWebServer.enqueue(createResourceServerUnauthorizedResponse());
		mockWebServer.enqueue(createAuthServerGrantRequestSuccessResponse());
		mockWebServer.enqueue(createResourceServerSuccessResponse(secretWords));

		mockMvc.perform(get("/api/words")).andExpect(status().isInternalServerError());
		mockMvc.perform(get("/api/words")).andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(expected), true));

		verify(authorizationServerAuthorizationSuccessHandler, times(2)).onAuthorizationSuccess(any(), any(), any());
		verify(resourceServerAuthorizationFailureHandler, times(1)).onAuthorizationFailure(any(), any(), any());

		RecordedRequest firstAuthServerRequest = mockWebServer.takeRequest();
		assertThat(firstAuthServerRequest.getRequestUrl()).isEqualTo(HttpUrl.parse(
				clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID).getProviderDetails().getTokenUri()));
		assertThat(firstAuthServerRequest.getHeader(HttpHeaders.CONTENT_TYPE))
				.isEqualTo(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8).toString());

		RecordedRequest firstResourceServerRequest = mockWebServer.takeRequest();
		assertThat(firstResourceServerRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));
		/*
		 * RecordedRequest.getUtf8Body() is deprecated but the suggested alternative,
		 * RecordedRequest.getBody().readUtf8(), is returning an okhttp.Buffer which is
		 * not found. Need to research this.
		 */
		assertThat(firstAuthServerRequest.getUtf8Body()).isEqualTo(createTokenResponseAsFormData());

		RecordedRequest secondAuthServerRequest = mockWebServer.takeRequest();
		assertThat(secondAuthServerRequest.getRequestUrl()).isEqualTo(HttpUrl.parse(
				clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID).getProviderDetails().getTokenUri()));
		assertThat(secondAuthServerRequest.getHeader(HttpHeaders.CONTENT_TYPE))
				.isEqualTo(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8).toString());

		RecordedRequest secondResourceServerRequest = mockWebServer.takeRequest();
		assertThat(secondResourceServerRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));
	}

	MockResponse createResourceServerSuccessResponse(List<String> secretWords) throws JsonProcessingException {
		return new MockResponse().setResponseCode(200).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
				.setBody(objectMapper.writeValueAsString(secretWords));
	}

	MockResponse createAuthServerGrantRequestSuccessResponse() {
		return new MockResponse().setResponseCode(200).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
				.setBody(createTokenResponseBody());
	}

	MockResponse createResourceServerUnauthorizedResponse() {
		return new MockResponse().setResponseCode(401);
	}

	String createTokenResponseBody() {
		try {
			return objectMapper.writeValueAsString(createTokenResponse());
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize token response", e);
		}
	}

	Map<String, Object> createTokenResponse() {
		// @formatter:off
		return Map.of(
				OAuth2ParameterNames.ACCESS_TOKEN, DUMMY_ACCESS_TOKEN,
				OAuth2ParameterNames.EXPIRES_IN, 3600,
				OAuth2ParameterNames.REFRESH_TOKEN, "dummy-refresh-token",
				OAuth2ParameterNames.TOKEN_TYPE, BEARER.getValue()
		);
		// @formatter:on
	}

	/*
	 * There must be a better way to do this.
	 * org.springframework.http.converter.FormHttpMessageConverter.writeForm does what we
	 * need but it is tightly coupled to an HttpOutputMessage
	 */
	String createTokenResponseAsFormData() {
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath("");
		createGrantRequestFormData().forEach(uriComponentsBuilder::queryParam);
		UriComponents build = uriComponentsBuilder.build();
		String s = build.toString();
		return s.substring(1); // Trim the leading '?' from the query params string
	}

	/*
	 * Reusing some of the code in Spring OAuth's
	 * DefaultClientCredentialsTokenResponseClient.getTokenResponse
	 */
	@SuppressWarnings("unchecked")
	MultiValueMap<String, String> createGrantRequestFormData() {
		ClientRegistration myClientRegistration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
		var grantRequest = new OAuth2ClientCredentialsGrantRequest(myClientRegistration);
		RequestEntity<?> requestEntity = new OAuth2ClientCredentialsGrantRequestEntityConverter().convert(grantRequest);
		return (MultiValueMap<String, String>) requestEntity.getBody();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		Supplier<Instant> timestampSupplier() {
			return () -> FIXED_TIMESTAMP;
		}

		@Bean("oauth2RestTemplate")
		RestTemplate oauth2RestTemplate() {
			RestTemplate restTemplate = new RestTemplate(
					Arrays.asList(new FormHttpMessageConverter(), new OAuth2AccessTokenResponseHttpMessageConverter()));
			restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
			return restTemplate;
		}

		@Bean
		OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient(
				@Qualifier("oauth2RestTemplate") RestTemplate oauth2RestTemplate) {
			var defaultTokenResponseClient = new DefaultClientCredentialsTokenResponseClient();
			defaultTokenResponseClient.setRestOperations(oauth2RestTemplate);
			return defaultTokenResponseClient;
		}

	}

}
