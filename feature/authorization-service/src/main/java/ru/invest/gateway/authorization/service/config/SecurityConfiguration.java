package ru.invest.gateway.authorization.service.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
 * уведомления и портфель - только с сессией. Календарь выплат по личной ссылке открыт всегда - его защищает токен,
 * webhook платёжного провайдера - тоже всегда, его защищает подпись.
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
    /**
     * Webhook платёжного провайдера (Т-Касса): без сессии и CSRF-токена, подлинность проверяет invest-api по подписи в
     * теле. Открыт и без публичного режима.
     */
    public static final String PAYMENT_NOTIFICATIONS = "/api/payments/notifications/*";
    private static final String API_PATTERN = "/api/**";
    private static final String BONDS_PATTERN = "/api/bonds/**";
    /**
     * POST, открытые без входа: чтение с фильтрами в теле (страница и расчёт калькулятора) и учёт перехода к
     * брокеру-партнёру (счётчик без пользователя, гость тоже кликает). Открыты поимённо: новый POST под /api/bonds не
     * станет публичным сам собой.
     */
    private static final String[] PUBLIC_BOND_POSTS = {
            "/api/bonds/*/page", "/api/bonds/*/calculation", "/api/bonds/*/broker-links/*/click"};
    /**
     * Календарь выплат по личной ссылке (.ics): Google, Apple, Outlook забирают его без сессии, пользователя
     * определяет токен в пути (проверяет invest-api). Открыт и без публичного режима - это не общие данные.
     */
    private static final String CALENDAR_FEED = "/api/calendar/*";

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
                    requests.requestMatchers(HttpMethod.GET, CALENDAR_FEED).permitAll()
                            .requestMatchers(HttpMethod.HEAD, CALENDAR_FEED).permitAll()
                            .requestMatchers(HttpMethod.POST, PAYMENT_NOTIFICATIONS).permitAll();
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
                // cookie ставится и без сессии, поэтому анонимные POST облигаций тоже защищены.
                // Webhook провайдера приходит не из браузера: токена у него нет, защищает подпись
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(
                        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, PAYMENT_NOTIFICATIONS)))
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
