package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import ru.invest.gateway.authorization.service.filter.LoginReturnFilter;
import ru.invest.gateway.authorization.service.handler.LoggingAuthenticationSuccessHandler;

import static org.assertj.core.api.Assertions.assertThat;

class LoginReturnTest {
    private final LoggingAuthenticationSuccessHandler handler = new LoggingAuthenticationSuccessHandler();

    @ParameterizedTest
    @ValueSource(strings = {"/", "/bonds", "/bonds/RU000A0JX0J2?tab=news", "/portfolio"})
    void ownPathsAreSafe(final String path) {
        assertThat(LoginReturnFilter.isSafePath(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bonds", "https://evil.example/", "//evil.example/", "/\\evil.example/", "/bonds\r\nSet-Cookie: x=1"})
    void foreignOrBrokenPathsAreRejected(final String path) {
        assertThat(LoginReturnFilter.isSafePath(path)).isFalse();
    }

    @Test
    void afterLoginRedirectsToReturnPathOnce() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        request.getSession().setAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE, "/bonds/RU000A0JX0J2");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken("ivan", null, "USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/bonds/RU000A0JX0J2");
        assertThat(request.getSession().getAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void withoutReturnPathRedirectsToRoot() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken("ivan", null, "USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }
}
