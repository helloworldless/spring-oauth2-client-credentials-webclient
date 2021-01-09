# Handling OAuth Client Credentials Authorization Transparently with Spring

_Reference project demonstrating how to transparently handle OAuth 2 Client Credentials authorization request when
communicating from backend to backend, i.e. from client to resource server._

This example shows how the client can call the resource server using the Spring's `WebClient` without having to write a
bunch of imperative logic such as:

- Making the grant request to the authorization server
- Checking if the token is expired or about to expire before calling the resource server
- Handling 401 Unauthorized responses from the resource server

Spring Security's OAuth2 Client abstractions handle all of this for us once we have the proper configuration in place.

**To the caller, it is as if the resource server can be called directly.**

## Related Blog Posts

- [How to Automatically Refresh OAuth2 Client Credentials in Spring](https://davidagood.com/oauth-client-credentials-auto-refresh-spring/)
- [How To Completely Disable HTTP Security in Spring Security](https://davidagood.com/spring-security-disable-http-security/)

## Running The Code

Rather than running a separate authorization server and resource server and making live requests, this capability is
demonstrated through an integration test, `AuthorizedWebClientIT`.

**To be clear: the main, "production" code is not intended to be run. It will start but calling `/api/words` will lead
to an error. Instead, you should run the test code.**

The Spring configuration which enables all of this is in `AuthorizedWebClientConfig` with overrides for integration
testing in `AuthorizedWebClientIT.TestConfig`.

## How This Is All Works Under The Hood

Almost all the classes mentioned below are in the package `org.springframework.security.oauth2.client`
or `org.springframework.security.oauth2.client.web`
from `org.springframework.security:spring-security-oauth2-client`...

1. `ServletOAuth2AuthorizedClientExchangeFilterFunction.filter`
    1. Takes the resource server request
    1. Uses existing authorization or, if necessary, handles making a new authorization request (see next step)
    1. Adds bearer token to resource server request
1. `ServletOAuth2AuthorizedClientExchangeFilterFunction.authorizeClient`
1. Calls `OAuth2AuthorizedClientManager.authorize`

Using the `AuthorizedClientServiceOAuth2AuthorizedClientManager` as the concrete `OAuth2AuthorizedClientManager`:

1. `AuthorizedClientServiceOAuth2AuthorizedClientManager.authorize`
    1. Calls its member `OAuth2AuthorizedClientService.loadAuthorizedClient`
1. `InMemoryOAuth2AuthorizedClientService.loadAuthorizedClient`
    1. Stores authorized clients in a `ConcurrentHashMap`
    1. The only parameters you need to retrieve an authorized client are:
        1. clientRegistrationId - whatever arbitrary registration id you've given, e.g.
           in `spring.security.oauth2.client.registration.<client-registration-id-here>`
        1. principalName - "anonymousUser" in this case,
           see `ServletOAuth2AuthorizedClientExchangeFilterFunction.ANONYMOUS_AUTHENTICATION`

Using the `DefaultOAuth2AuthorizedClientManager` as the concrete `OAuth2AuthorizedClientManager`:

1. `DefaultOAuth2AuthorizedClientManager.authorize`
    1. Call its member `OAuth2AuthorizedClientRepository.loadAuthorizedClient`
1. `AuthenticatedPrincipalOAuth2AuthorizedClientRepository.loadAuthorizedClient`
    1. If the authenticated user is anonymous, defers to:
1. `HttpSessionOAuth2AuthorizedClientRepository.loadAuthorizedClient`
    1. This looks for the authorized client in the `HttpServletRequest`'s `HttpSession`
1. `DelegatingOAuth2AuthorizedClientProvider.authorize`
1. `ClientCredentialsOAuth2AuthorizedClientProvider.authorize`
    1. This is where it checks if an authorization request needs to be made
       (if the authorized client is `null` or the token has expired)
    1. Returns `null` to signal that a new authorization request did not need to be made because an unexpired authorized
       client already existed in which case the AuthorizedClientManager will just use the existing auth

## Other Info

If an `ClientRegistrationRepository` bean is not configured, an `InMemoryClientRegistrationRepository` is autowired
by `OAuth2ClientRegistrationRepositoryConfiguration`. It searches for OAuth client registrations in application
properties: `spring.security.oauth2.client.registration`. See `application.yml` for an example.

## TODO

- Add test for RemoveAuthorizedClientOAuth2AuthorizationFailureHandler