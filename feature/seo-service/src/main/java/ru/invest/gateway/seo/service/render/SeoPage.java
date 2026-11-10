package ru.invest.gateway.seo.service.render;

import org.springframework.http.HttpStatus;

/**
 * Что вставить в оболочку UI.
 *
 * @param status      код ответа: 404 для неизвестной облигации, чтобы поисковик не индексировал пустую страницу
 * @param title       {@code <title>} и og:title
 * @param description meta description и og:description
 * @param path        путь страницы без query - для canonical и og:url; null - без canonical
 * @param ogType      og:type
 * @param indexable   false - {@code <meta name="robots" content="noindex">}
 * @param bodyHtml    уже экранированный HTML содержимого: кладётся в {@code #root}, React заменяет его при загрузке
 */
public record SeoPage(HttpStatus status, String title, String description, String path, String ogType,
                      boolean indexable, String bodyHtml) {
}
