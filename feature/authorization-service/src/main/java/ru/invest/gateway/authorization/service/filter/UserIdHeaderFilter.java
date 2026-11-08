package ru.invest.gateway.authorization.service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Передаёт пользователя в invest-api заголовком X-User-Id: sub из OIDC-сессии Keycloak. Заголовок с тем же именем
 * из браузера удаляется всегда - иначе его можно было бы подделать. Без входа заголовка нет.
 * <p>
 * Стоит после цепочки Spring Security: к этому моменту SecurityContext уже восстановлен из сессии.
 */
public class UserIdHeaderFilter extends OncePerRequestFilter {
    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new UserIdRequest(request, currentUserId()), response);
    }

    private static String currentUserId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof final OidcUser oidcUser) {
            return oidcUser.getSubject();
        }
        return authentication.getName();
    }

    private static final class UserIdRequest extends HttpServletRequestWrapper {
        private final String userId;

        private UserIdRequest(final HttpServletRequest request, final String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(final String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(userId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            final List<String> names = Collections.list(super.getHeaderNames())
                    .stream()
                    .filter(name -> !USER_ID_HEADER.equalsIgnoreCase(name))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (userId != null) {
                names.add(USER_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
