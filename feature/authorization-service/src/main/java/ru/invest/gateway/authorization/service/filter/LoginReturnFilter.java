package ru.invest.gateway.authorization.service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Запоминает, куда вернуть пользователя после входа: UI начинает логин с
 * {@code /oauth2/authorization/keycloak?returnTo=/bonds/RU000A...}, адрес кладётся в сессию,
 * {@code LoggingAuthenticationSuccessHandler} после входа ведёт туда.
 * <p>
 * Принимается только путь своего origin (начинается с одного {@code /}) - иначе это open redirect.
 * Стоит в цепочке Spring Security перед OAuth2AuthorizationRequestRedirectFilter.
 */
public class LoginReturnFilter extends OncePerRequestFilter {
    public static final String RETURN_TO_PARAMETER = "returnTo";
    public static final String RETURN_TO_ATTRIBUTE = LoginReturnFilter.class.getName() + ".RETURN_TO";
    private static final String AUTHORIZATION_PREFIX = "/oauth2/authorization/";
    private static final int MAX_LENGTH = 2048;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTHORIZATION_PREFIX);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final String returnTo = request.getParameter(RETURN_TO_PARAMETER);
        if (isSafePath(returnTo)) {
            request.getSession().setAttribute(RETURN_TO_ATTRIBUTE, returnTo);
        } else {
            // адрес от прошлой незавершённой попытки входа не должен сработать для этой
            final HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(RETURN_TO_ATTRIBUTE);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Путь на том же origin: {@code /bonds?scope=local}. Не подходят абсолютные адреса, {@code //host} и
     * {@code /\host} (браузер понимает оба как другой хост), управляющие символы.
     */
    public static boolean isSafePath(final String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH || path.charAt(0) != '/') {
            return false;
        }
        if (path.length() > 1 && (path.charAt(1) == '/' || path.charAt(1) == '\\')) {
            return false;
        }
        return path.chars().noneMatch(ch -> Character.isISOControl(ch) || ch == '\\');
    }
}
