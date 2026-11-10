package ru.invest.gateway.authorization.service.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Состояние входа для UI: вошёл ли пользователь и его имя. Доступен без входа и всегда отвечает {@code 200} -
 * UI по нему решает, показывать «Войти» или профиль, не вызывая {@code 401}.
 */
@RestController
public class SessionController {
    public static final String SESSION_PATH = "/api/session";

    @GetMapping(SESSION_PATH)
    public SessionResponse session(final Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new SessionResponse(false, null);
        }
        return new SessionResponse(true, username(authentication));
    }

    private static String username(final Authentication authentication) {
        if (authentication.getPrincipal() instanceof final OidcUser oidcUser && oidcUser.getPreferredUsername() != null) {
            return oidcUser.getPreferredUsername();
        }
        return authentication.getName();
    }

    /**
     * @param authenticated есть ли сессия с входом
     * @param username      preferred_username из Keycloak; у гостя - {@code null}
     */
    public record SessionResponse(boolean authenticated, String username) {
    }
}
