package ru.invest.gateway.authorization.service.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import ru.invest.gateway.authorization.service.filter.LoginReturnFilter;

import java.io.IOException;

/**
 * Пишет в лог успешный вход и возвращает на страницу, с которой UI начал вход ({@link LoginReturnFilter}); без неё -
 * как обработчик Spring Security по умолчанию (сохранённый запрос или {@code /}).
 */
@Slf4j
@Component
public class LoggingAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
                                        final Authentication authentication) throws ServletException, IOException {
        log.info("Login succeeded: user={}, ip={}", resolveUsername(authentication), request.getRemoteAddr());
        final String returnTo = takeReturnTo(request);
        if (returnTo != null) {
            // UI начал вход со своей страницы (?returnTo=) - возвращаем на неё, а не на сохранённый запрос или /
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, returnTo);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static String takeReturnTo(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        final Object returnTo = session.getAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE);
        session.removeAttribute(LoginReturnFilter.RETURN_TO_ATTRIBUTE);
        // путь проверен при сохранении; повторная проверка - на случай, если атрибут положил кто-то другой
        return returnTo instanceof final String path && LoginReturnFilter.isSafePath(path) ? path : null;
    }

    private String resolveUsername(final Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser && oidcUser.getPreferredUsername() != null) {
            return oidcUser.getPreferredUsername();
        }
        return authentication.getName();
    }
}
