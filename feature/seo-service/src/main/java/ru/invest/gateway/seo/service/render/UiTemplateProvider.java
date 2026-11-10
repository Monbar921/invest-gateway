package ru.invest.gateway.seo.service.render;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * {@code index.html} собранного UI - оболочка SEO-страницы: в нём ссылки на бандл и стили с хешами в именах, поэтому
 * он берётся у самого UI, а не хранится в gateway. Держится в памяти {@code ttl}; UI недоступен - отдаётся последний
 * полученный, а если его ещё не было - минимальная оболочка без скриптов (поисковику хватит, у человека не будет SPA).
 */
@Slf4j
public class UiTemplateProvider {
    static final String FALLBACK_TEMPLATE = """
            <!doctype html>
            <html lang="ru">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title></title>
              </head>
              <body>
                <div id="root"></div>
              </body>
            </html>
            """;

    private final RestClient uiClient;
    private final Duration ttl;
    private final Clock clock;

    private String template;
    private Instant fetchedAt = Instant.MIN;

    public UiTemplateProvider(final RestClient uiClient, final Duration ttl, final Clock clock) {
        this.uiClient = uiClient;
        this.ttl = ttl;
        this.clock = clock;
    }

    public synchronized String getTemplate() {
        final Instant now = clock.instant();
        if (template != null && fetchedAt.plus(ttl).isAfter(now)) {
            return template;
        }
        try {
            final String fetched = uiClient.get().uri("/index.html").accept(MediaType.TEXT_HTML).retrieve().body(String.class);
            if (fetched != null && fetched.contains("id=\"root\"")) {
                template = fetched;
                fetchedAt = now;
                return template;
            }
            log.warn("UI index.html has no root element, using previous template");
        } catch (final RestClientException exception) {
            log.warn("UI index.html is not available: {}", exception.getMessage());
        }
        // следующая попытка - не раньше чем через ttl, чтобы недоступный UI не тормозил каждую страницу
        fetchedAt = now;
        if (template == null) {
            template = FALLBACK_TEMPLATE;
        }
        return template;
    }
}
