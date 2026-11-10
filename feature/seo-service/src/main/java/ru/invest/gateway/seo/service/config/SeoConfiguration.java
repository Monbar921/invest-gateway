package ru.invest.gateway.seo.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.invest.gateway.seo.service.client.InvestApiSeoClient;
import ru.invest.gateway.seo.service.controller.BaseUrlResolver;
import ru.invest.gateway.seo.service.render.UiTemplateProvider;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * SEO-страницы включаются вместе с публичным режимом: без него облигации без входа не открываются и показывать
 * поисковику нечего. Пути страниц открыты без входа в SecurityConfiguration.
 */
@Configuration
@EnableConfigurationProperties(SeoProperties.class)
@ConditionalOnBooleanProperty(name = {"invest.gateway.seo.enabled", "invest.gateway.public-access.enabled"},
        matchIfMissing = true)
public class SeoConfiguration {

    @Bean
    public InvestApiSeoClient investApiSeoClient(@Value("${invest.gateway.invest-api-uri}") final String investApiUri,
                                                 final SeoProperties properties) {
        return new InvestApiSeoClient(restClient(investApiUri + "/internal/rest", properties));
    }

    @Bean
    public UiTemplateProvider uiTemplateProvider(final SeoProperties properties) {
        return new UiTemplateProvider(restClient(properties.uiUrl(), properties), properties.templateTtl(), Clock.systemUTC());
    }

    @Bean
    public BaseUrlResolver baseUrlResolver(final SeoProperties properties) {
        return new BaseUrlResolver(properties.publicUrl());
    }

    private static RestClient restClient(final String baseUrl, final SeoProperties properties) {
        final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
