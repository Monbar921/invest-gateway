package ru.invest.gateway;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.channels.ClosedChannelException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keycloak в тесте не поднимается: регистрация клиента задана бином с явными URI,
 * поэтому автоконфигурация не ходит в discovery по issuer-uri.
 */
@SpringBootTest(properties = "invest.gateway.invest-api-uri=http://127.0.0.1:1")
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    @TestConfiguration
    static class KeycloakStubConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
                    .clientId("invest-gateway")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("http://keycloak.test/auth")
                    .tokenUri("http://keycloak.test/token")
                    .build());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiWithoutSessionReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bonds/all/page"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pageWithoutSessionRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/bonds/all/page").with(oauth2Login()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutReturnsLocationInsteadOfRedirect() throws Exception {
        // без end_session_endpoint у провайдера handler отдаёт fallback-адрес - проверяем только способ ответа
        // как в браузере: gateway ставит cookie XSRF-TOKEN, UI возвращает её значение в заголовке
        final Cookie csrfCookie = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/logout").with(oauth2Login()).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/"));
    }

    @Test
    void authenticatedApiRequestIsRoutedToInvestApi() {
        // invest-api на порту 1 недоступен: по ошибке проксирования видно, куда gateway отправил запрос
        assertThatThrownBy(() -> mockMvc.perform(get("/api/bonds/all/page").with(oauth2Login())))
                .hasRootCauseInstanceOf(ClosedChannelException.class)
                .hasMessageContaining("http://127.0.0.1:1/internal/rest/bonds/all/page");
    }
}
