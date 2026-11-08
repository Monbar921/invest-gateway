package ru.invest.gateway.authorization.service.config;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.invest.gateway.authorization.service.filter.UserIdHeaderFilter;

@Configuration
public class UserIdHeaderConfiguration {

    /**
     * Сразу после цепочки Spring Security, чтобы пользователь из сессии уже был в SecurityContext.
     */
    @Bean
    public FilterRegistrationBean<UserIdHeaderFilter> userIdHeaderFilter() {
        final FilterRegistrationBean<UserIdHeaderFilter> registration =
                new FilterRegistrationBean<>(new UserIdHeaderFilter());
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
