package ru.invest.gateway.authorization.service.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import ru.invest.gateway.authorization.service.controller.SessionController;
import ru.invest.gateway.authorization.service.filter.LoginReturnFilter;
import ru.invest.gateway.authorization.service.handler.LoggingAuthenticationFailureHandler;
import ru.invest.gateway.authorization.service.handler.LoggingAuthenticationSuccessHandler;

import java.util.List;

/**
 * BFF: gateway сам проходит OAuth2-логин в Keycloak и держит токены в HTTP-сессии.
 * Браузер получает только session cookie, downstream-сервисы токенов не видят.
 * <p>
 * Публичный режим ({@link PublicAccessProperties#enabled()}): чтение облигаций открыто без входа,
 * уведомления и портфель - только с сессией.
 */
@Configuration
@EnableConfigurationProperties(PublicAccessProperties.class)
public class SecurityConfiguration {
    /**
     * Страницы для поисковиков (модуль seo-service): готовый HTML облигации, эмитента, подборки и списка, sitemap и
     * robots. Открыты вместе с чтением облигаций - показывают те же данные.
     */
    public static final List<String> PUBLIC_PAGES = List.of(
            "/bonds", "/bonds/*", "/issuers/*", "/collections/*", "/sitemap.xml", "/robots.txt");
    private static final String API_PATTERN = "/api/**";
    private static final String BONDS_PATTERN = "/api/bonds/**";
    /**
     * POST, открытые без входа: чтение с фильтрами в теле (страница и расчёт калькулятора) и учёт перехода к
     * брокеру-партнёру (счётчик без пользователя, гость тоже кликает). Открыты поимённо: новый POST под /api/bonds не
     * станет публичным сам собой.
     */
    private static final String[] PUBLIC_BOND_POSTS = {
            "/api/bonds/*/page", "/api/bonds/*/calculation", "/api/bonds/*/broker-links/*/click"};

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final ClientRegistrationRepository clientRegistrationRepository,
                                                   final LoggingAuthenticationSuccessHandler successHandler,
                                                   final LoggingAuthenticationFailureHandler failureHandler,
                                                   final PublicAccessProperties publicAccess) {
        // после logout Keycloak завершает свою сессию и возвращает пользователя на главную UI
        final var logoutSuccessHandler = getOidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

        http
                .authorizeHttpRequests(requests -> {
                    // ответ ошибки (sendError -> /error) отдаётся как есть: иначе гость вместо 403/404 получил бы 401,
                    // а UI на 401 уводит на логин
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    requests.requestMatchers("/actuator/health", SessionController.SESSION_PATH).permitAll();
                    if (publicAccess.enabled()) {
                        requests.requestMatchers(HttpMethod.GET, BONDS_PATTERN).permitAll()
                                .requestMatchers(HttpMethod.HEAD, BONDS_PATTERN).permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLIC_PAGES.toArray(String[]::new)).permitAll()
                                .requestMatchers(HttpMethod.HEAD, PUBLIC_PAGES.toArray(String[]::new)).permitAll()
                                .requestMatchers(HttpMethod.POST, PUBLIC_BOND_POSTS).permitAll();
                    }
                    requests.anyRequest().authenticated();
                })
                // UI начинает вход с ?returnTo=<своя страница>, после входа gateway вернёт туда
                .addFilterBefore(new LoginReturnFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
                .oauth2Login(login -> login
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
                // SPA читает токен из cookie XSRF-TOKEN и отправляет его в заголовке X-XSRF-TOKEN;
                // cookie ставится и без сессии, поэтому анонимные POST облигаций тоже защищены
                .csrf(CsrfConfigurer::spa)
                // fetch из UI должен получить 401, а не HTML-страницу логина Keycloak после редиректа
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern(API_PATTERN)));
        return http.build();
    }

    private OidcClientInitiatedLogoutSuccessHandler getOidcClientInitiatedLogoutSuccessHandler(
            final ClientRegistrationRepository clientRegistrationRepository) {
        final var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");
        // UI выходит через fetch (CSRF-токен идёт в заголовке), а fetch не может пройти редирект на Keycloak
        // с другого origin: отдаём адрес выхода в Location, браузер переходит туда сам
        logoutSuccessHandler.setRedirectStrategy((request, response, url) -> {
            response.setStatus(HttpStatus.ACCEPTED.value());
            response.setHeader(HttpHeaders.LOCATION, url);
        });
        return logoutSuccessHandler;
    }
}
