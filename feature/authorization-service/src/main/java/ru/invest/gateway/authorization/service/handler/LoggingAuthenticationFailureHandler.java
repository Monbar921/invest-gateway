package ru.invest.gateway.authorization.service.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Пишет в лог неудачный вход и дальше ведёт на страницу ошибки, как обработчик oauth2Login по умолчанию.
 */
@Slf4j
@Component
public class LoggingAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public LoggingAuthenticationFailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(final HttpServletRequest request, final HttpServletResponse response,
                                        final AuthenticationException exception) throws IOException, ServletException {
        final String reason = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("Login failed: reason={}, ip={}, message={}", reason, request.getRemoteAddr(), exception.getMessage());
        super.onAuthenticationFailure(request, response, exception);
    }
}
