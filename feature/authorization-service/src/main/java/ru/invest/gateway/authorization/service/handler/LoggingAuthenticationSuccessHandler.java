package ru.invest.gateway.authorization.service.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Пишет в лог успешный вход и дальше работает как обработчик Spring Security по умолчанию.
 */
@Slf4j
@Component
public class LoggingAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
                                        final Authentication authentication) throws ServletException, IOException {
        log.info("Login succeeded: user={}, ip={}", resolveUsername(authentication), request.getRemoteAddr());
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static String resolveUsername(final Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser && oidcUser.getPreferredUsername() != null) {
            return oidcUser.getPreferredUsername();
        }
        return authentication.getName();
    }
}
