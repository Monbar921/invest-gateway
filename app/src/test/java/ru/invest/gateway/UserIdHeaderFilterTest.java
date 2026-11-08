package ru.invest.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import ru.invest.gateway.authorization.service.filter.UserIdHeaderFilter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdHeaderFilterTest {
    private static final String SUB = "0f8c2c9e-5b7a-4d0e-9c1a-2f4e6a8b0c1d";

    private final UserIdHeaderFilter filter = new UserIdHeaderFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void subOfOidcUserIsPassed() throws Exception {
        final DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"),
                new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", SUB)));
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak"));

        final HttpServletRequest forwarded = filter(request("someone-else"));

        assertThat(forwarded.getHeader(UserIdHeaderFilter.USER_ID_HEADER)).isEqualTo(SUB);
        assertThat(Collections.list(forwarded.getHeaders("x-user-id"))).containsExactly(SUB);
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .filteredOn(UserIdHeaderFilter.USER_ID_HEADER::equalsIgnoreCase)
                .hasSize(1);
        assertThat(forwarded.getHeader("Accept")).isEqualTo("application/json");
    }

    @Test
    void spoofedHeaderIsRemovedWithoutLogin() throws Exception {
        final HttpServletRequest forwarded = filter(request("someone-else"));

        assertThat(forwarded.getHeader(UserIdHeaderFilter.USER_ID_HEADER)).isNull();
        assertThat(Collections.list(forwarded.getHeaders(UserIdHeaderFilter.USER_ID_HEADER))).isEmpty();
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .noneMatch(UserIdHeaderFilter.USER_ID_HEADER::equalsIgnoreCase);
    }

    @Test
    void anonymousUserHasNoHeader() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(filter(request("someone-else")).getHeader(UserIdHeaderFilter.USER_ID_HEADER)).isNull();
    }

    private static MockHttpServletRequest request(final String spoofedUserId) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts/telegram");
        request.addHeader("x-user-id", spoofedUserId);
        request.addHeader("Accept", "application/json");
        return request;
    }

    private HttpServletRequest filter(final MockHttpServletRequest request) throws Exception {
        final MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }
}
