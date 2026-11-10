package ru.invest.gateway.seo.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * SEO-страницы: готовый HTML облигаций, эмитентов и подборок, sitemap.xml и robots.txt.
 *
 * @param enabled       отдавать ли SEO-страницы; работают только вместе с публичным режимом
 * @param publicUrl     адрес сайта для canonical, Open Graph и sitemap ({@code https://bonds.example}); пусто - из
 *                      запроса (за прокси - из {@code X-Forwarded-*})
 * @param uiUrl         откуда брать {@code index.html} собранного UI (в нём ссылки на бандл): nginx UI на стенде,
 *                      Vite в dev
 * @param templateTtl   сколько держать {@code index.html} в памяти: после выкладки UI имена бандлов меняются
 * @param siteName      название сайта в title и og:site_name
 * @param timeout       таймаут запросов к invest-api и UI
 */
@ConfigurationProperties("invest.gateway.seo")
public record SeoProperties(@DefaultValue("true") boolean enabled,
                            String publicUrl,
                            @DefaultValue("http://127.0.0.1:5173") String uiUrl,
                            @DefaultValue("1m") Duration templateTtl,
                            @DefaultValue("Облигации") String siteName,
                            @DefaultValue("5s") Duration timeout) {
}
