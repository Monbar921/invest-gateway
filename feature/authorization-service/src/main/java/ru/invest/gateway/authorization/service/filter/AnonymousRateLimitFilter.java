package ru.invest.gateway.authorization.service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ограничивает частоту анонимных запросов к {@code /api/**} по IP - защита публичного режима от выкачивания данных.
 * Вошедших пользователей не ограничивает. Сверх лимита - {@code 429} с {@code Retry-After} и ProblemDetail.
 * <p>
 * Стоит после цепочки Spring Security (пользователь из сессии уже известен). IP - {@code getRemoteAddr()}: за прокси
 * его подставляет ForwardedHeaderFilter из {@code X-Forwarded-For} ({@code server.forward-headers-strategy: framework}),
 * поэтому прокси перед gateway должен сам выставлять этот заголовок, а не доверять присланному клиентом.
 */
@Slf4j
public class AnonymousRateLimitFilter extends OncePerRequestFilter {
    private static final String API_PREFIX = "/api/";
    private static final String TOO_MANY_REQUESTS_BODY = """
            {"type":"about:blank","title":"Too Many Requests","status":429,\
            "detail":"Слишком много запросов без входа. Подождите немного или войдите."}""";

    private final AnonymousRateLimiter limiter;

    public AnonymousRateLimitFilter(final AnonymousRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        final long retryAfter = limiter.tryAcquire(request.getRemoteAddr());
        if (retryAfter == 0) {
            filterChain.doFilter(request, response);
            return;
        }
        log.debug("Anonymous rate limit exceeded: ip={}, uri={}", request.getRemoteAddr(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(TOO_MANY_REQUESTS_BODY);
    }

    private static boolean isAuthenticated() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
