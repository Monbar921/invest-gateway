package ru.invest.gateway.seo.service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Адрес сайта для абсолютных ссылок (canonical, og:url, sitemap): из настройки, а если её нет - из запроса. За прокси
 * адрес запроса уже исправлен ForwardedHeaderFilter по {@code X-Forwarded-*}
 * ({@code server.forward-headers-strategy: framework}).
 */
public class BaseUrlResolver {
    private final String publicUrl;

    public BaseUrlResolver(final String publicUrl) {
        this.publicUrl = publicUrl == null || publicUrl.isBlank() ? null : stripSlash(publicUrl.strip());
    }

    public String resolve(final HttpServletRequest request) {
        if (publicUrl != null) {
            return publicUrl;
        }
        return stripSlash(ServletUriComponentsBuilder.fromContextPath(request).replaceQuery(null).build().toUriString());
    }

    private static String stripSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
