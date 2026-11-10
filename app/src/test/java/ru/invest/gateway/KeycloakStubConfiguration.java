package ru.invest.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Keycloak в тестах не поднимается: регистрация клиента задана бином с явными URI,
 * поэтому автоконфигурация не ходит в discovery по issuer-uri.
 */
@TestConfiguration
class KeycloakStubConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
                .clientId("invest-gateway")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://keycloak.test/auth")
                .tokenUri("http://keycloak.test/token")
                .scope("openid")
                .build());
    }
}
