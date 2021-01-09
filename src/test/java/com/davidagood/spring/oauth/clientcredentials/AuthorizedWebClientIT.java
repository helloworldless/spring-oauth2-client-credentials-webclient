package com.davidagood.spring.oauth.clientcredentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

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
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
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

	private MockRestServiceServer mockServer;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) {
		r.add("secret-words-client.url", () -> "http://localhost:" + MOCK_SERVER_PORT);
	}

	@BeforeAll
	static void beforeAll() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start(MOCK_SERVER_PORT);
	}

	@BeforeEach
	void setUp() {
		this.mockServer = MockRestServiceServer.createServer(oauth2RestTemplate);
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
		var tokenUri = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID).getProviderDetails()
				.getTokenUri();
		mockServer.expect(once(), requestTo(tokenUri))
				.andExpect(content()
						.contentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8)))
				.andExpect(content().formData(createGrantRequestFormData()))
				.andRespond(withSuccess(createTokenResponseBody(), MediaType.APPLICATION_JSON));

		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
				.setBody(objectMapper.writeValueAsString(secretWords)));
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
				.setBody(objectMapper.writeValueAsString(secretWords)));

		var expected = SecretWordsDto.from(secretWords, FIXED_TIMESTAMP);

		mockMvc.perform(get("/api/words")).andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(expected), true));

		mockMvc.perform(get("/api/words")).andExpect(status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(expected), true));

		mockServer.verify();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));
		RecordedRequest secondRecordedRequest = mockWebServer.takeRequest();
		assertThat(secondRecordedRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo(String.format("%s %s", BEARER.getValue(), DUMMY_ACCESS_TOKEN));
	}

	private String createTokenResponseBody() {
		try {
			return objectMapper.writeValueAsString(createTokenResponse());
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize token response", e);
		}
	}

	private Map<String, Object> createTokenResponse() {
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
	 * Reusing some of the code in Spring OAuth's
	 * DefaultClientCredentialsTokenResponseClient.getTokenResponse
	 */
	@SuppressWarnings("unchecked")
	private MultiValueMap<String, String> createGrantRequestFormData() {
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
