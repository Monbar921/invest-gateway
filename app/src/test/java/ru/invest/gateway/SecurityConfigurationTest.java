package ru.invest.gateway;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.invest.gateway.authorization.service.filter.LoginReturnFilter;

import java.nio.channels.ClosedChannelException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// все тесты шлют анонимные запросы с одного адреса; лимит проверяет AnonymousRateLimitTest со своим значением
@SpringBootTest(properties = "invest.gateway.public-access.rate-limit.requests-per-minute=0")
@AutoConfigureMockMvc
@Import(KeycloakStubConfiguration.class)
class SecurityConfigurationTest {
    // invest-api на порту 1 недоступен: по ошибке проксирования видно, куда gateway отправил запрос
    private static final String INVEST_API = "http://127.0.0.1:1/internal/rest";

    @Autowired
    private MockMvc mockMvc;

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
        mockMvc.perform(withCsrf(post("/logout").with(oauth2Login())))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/"));
    }

    @Test
    void authenticatedApiRequestIsRoutedToInvestApi() {
        assertRoutedTo(get("/api/bonds/all/page").with(oauth2Login()), "/bonds/all/page");
    }

    // --- публичный режим: облигации без входа

    @Test
    void bondReadWithoutSessionIsRoutedToInvestApi() {
        assertRoutedTo(get("/api/bonds/RU000A0JX0J2"), "/bonds/RU000A0JX0J2");
        assertRoutedTo(get("/api/bonds/search").param("query", "ОФЗ"), "/bonds/search");
        assertRoutedTo(get("/api/bonds/news"), "/bonds/news");
    }

    @Test
    void bondPageAndCalculationWithoutSessionAreRoutedToInvestApi() throws Exception {
        assertRoutedTo(withCsrf(post("/api/bonds/local/page")), "/bonds/local/page");
        assertRoutedTo(withCsrf(post("/api/bonds/RU000A0JX0J2/calculation")), "/bonds/RU000A0JX0J2/calculation");
    }

    @Test
    void brokerClickWithoutSessionIsRoutedToInvestApi() throws Exception {
        // учёт перехода к брокеру-партнёру: гость кликает так же, как вошедший
        assertRoutedTo(withCsrf(post("/api/bonds/RU000A0JX0J2/broker-links/alpha/click")),
                "/bonds/RU000A0JX0J2/broker-links/alpha/click");
    }

    @Test
    void brokerClickWithoutCsrfTokenIsForbidden() throws Exception {
        // путь открыт без входа, но CSRF-проверка остаётся: чужой сайт не накрутит клики формой
        mockMvc.perform(post("/api/bonds/RU000A0JX0J2/broker-links/alpha/click"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherBrokerLinkPostsStillRequireLogin() throws Exception {
        // открыт только сам клик: соседние POST'ы под broker-links без сессии - 401
        mockMvc.perform(withCsrf(post("/api/bonds/RU000A0JX0J2/broker-links")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/bonds/RU000A0JX0J2/broker-links/alpha")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/bonds/RU000A0JX0J2/broker-links/alpha/click/extra")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPostWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/bonds/all/page"))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorDispatchOfGuestKeepsOriginalStatus() throws Exception {
        // MockMvc не делает error dispatch сам: в Tomcat sendError(403) уходит на /error, и без разрешения
        // гость получал 401 вместо 403
        mockMvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/bonds/all/page");
            return request;
        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherBondPostsStillRequireLogin() throws Exception {
        // открыты только чтение-POST'ы; новый изменяющий POST под /api/bonds публичным не станет
        mockMvc.perform(withCsrf(post("/api/bonds/RU000A0JX0J2/favorite")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfCookieIsIssuedWithoutSession() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    // --- уведомления и портфель - как раньше, только с сессией

    @Test
    void alertsAreRoutedToInvestApi() {
        assertRoutedTo(get("/api/alerts/telegram").with(oauth2Login()), "/alerts/telegram");
    }

    @Test
    void alertsWithoutSessionReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/alerts/telegram"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/alerts")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void portfolioIsRoutedToInvestApi() {
        assertRoutedTo(get("/api/portfolio").with(oauth2Login()), "/portfolio");
    }

    @Test
    void portfolioWithoutSessionReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/portfolio/positions")))
                .andExpect(status().isUnauthorized());
    }

    // --- календарь выплат по личной ссылке: без сессии, пользователя определяет токен

    @Test
    void calendarFeedWithoutSessionIsRoutedToInvestApi() {
        assertRoutedTo(get("/api/calendar/abc_DEF-123.ics"), "/calendar/abc_DEF-123.ics");
    }

    @Test
    void calendarSettingsStillRequireLogin() throws Exception {
        // ссылку создают и видят только в портфеле, с сессией
        mockMvc.perform(get("/api/portfolio/calendar/link"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/calendar/abc.ics")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/calendar/abc/nested.ics"))
                .andExpect(status().isUnauthorized());
    }

    // --- состояние входа для UI

    @Test
    void sessionOfGuest() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void sessionOfLoggedInUser() throws Exception {
        mockMvc.perform(get("/api/session").with(oidcLogin().idToken(token -> token.claim("preferred_username", "ivan"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("ivan"));
    }

    // --- возврат на страницу после входа

    @Test
    void loginRemembersReturnPath() throws Exception {
        final HttpSession session = mockMvc.perform(get("/oauth2/authorization/keycloak").param("returnTo", "/bonds/RU000A0JX0J2?tab=news"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.getAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE)).isEqualTo("/bonds/RU000A0JX0J2?tab=news");
    }

    @Test
    void loginIgnoresForeignReturnAddress() throws Exception {
        final HttpSession session = mockMvc.perform(get("/oauth2/authorization/keycloak").param("returnTo", "//evil.example/"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        assertThat(session == null ? null : session.getAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE)).isNull();
    }

    private void assertRoutedTo(final MockHttpServletRequestBuilder request, final String investApiPath) {
        assertThatThrownBy(() -> mockMvc.perform(request))
                .hasRootCauseInstanceOf(ClosedChannelException.class)
                .hasMessageContaining(INVEST_API + investApiPath);
    }

    /** Как в браузере: gateway ставит cookie XSRF-TOKEN, UI возвращает её значение в заголовке */
    private MockHttpServletRequestBuilder withCsrf(final MockHttpServletRequestBuilder request) throws Exception {
        final Cookie csrfCookie = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return request.cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue());
    }
}
