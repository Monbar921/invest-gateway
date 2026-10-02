package ru.invest.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * BFF: gateway сам проходит OAuth2-логин в Keycloak и держит токены в HTTP-сессии.
 * Браузер получает только session cookie, downstream-сервисы токенов не видят.
 */
@Configuration
public class SecurityConfiguration {
    private static final String API_PATTERN = "/api/**";

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final ClientRegistrationRepository clientRegistrationRepository) {
        // после logout Keycloak завершает свою сессию и возвращает пользователя на главную UI
        final var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");
        // UI выходит через fetch (CSRF-токен идёт в заголовке), а fetch не может пройти редирект на Keycloak
        // с другого origin: отдаём адрес выхода в Location, браузер переходит туда сам
        logoutSuccessHandler.setRedirectStrategy((request, response, url) -> {
            response.setStatus(HttpStatus.ACCEPTED.value());
            response.setHeader(HttpHeaders.LOCATION, url);
        });

        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
                // SPA читает токен из cookie XSRF-TOKEN и отправляет его в заголовке X-XSRF-TOKEN
                .csrf(csrf -> csrf.spa())
                // fetch из UI должен получить 401, а не HTML-страницу логина Keycloak после редиректа
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern(API_PATTERN)));
        return http.build();
    }
}
