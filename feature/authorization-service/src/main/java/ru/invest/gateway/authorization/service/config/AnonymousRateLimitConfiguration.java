package ru.invest.gateway.authorization.service.config;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.gateway.authorization.service.filter.AnonymousRateLimitFilter;
import ru.invest.gateway.authorization.service.filter.AnonymousRateLimiter;

import java.time.Clock;

@Configuration
public class AnonymousRateLimitConfiguration {

    /**
     * После цепочки Spring Security (вошедший пользователь уже известен и не ограничивается) и после UserIdHeaderFilter.
     */
    @Bean
    public FilterRegistrationBean<AnonymousRateLimitFilter> anonymousRateLimitFilter(final PublicAccessProperties properties) {
        final PublicAccessProperties.RateLimit rateLimit = properties.rateLimit();
        final AnonymousRateLimiter limiter = new AnonymousRateLimiter(
                rateLimit.requestsPerMinute(), rateLimit.maxClients(), Clock.systemUTC());
        final FilterRegistrationBean<AnonymousRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AnonymousRateLimitFilter(limiter));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
        return registration;
    }
}
