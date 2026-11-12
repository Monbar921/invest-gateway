package ru.invest.gateway.seo.service.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import ru.invest.gateway.seo.service.client.InvestApiSeoClient;
import ru.invest.gateway.seo.service.model.SeoModels;
import ru.invest.gateway.seo.service.render.Html;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@code sitemap.xml} - список облигаций, эмитентов и подборок из invest-api, и {@code robots.txt}.
 */
@Slf4j
@RestController
@ConditionalOnBooleanProperty(name = {"invest.gateway.seo.enabled", "invest.gateway.public-access.enabled"},
        matchIfMissing = true)
public class SitemapController {
    public static final String SITEMAP_PATH = "/sitemap.xml";
    public static final String ROBOTS_PATH = "/robots.txt";
    private static final MediaType XML_UTF8 = MediaType.parseMediaType("application/xml;charset=UTF-8");
    private static final MediaType TEXT_UTF8 = MediaType.parseMediaType("text/plain;charset=UTF-8");

    private final InvestApiSeoClient client;
    private final BaseUrlResolver baseUrlResolver;

    public SitemapController(final InvestApiSeoClient client, final BaseUrlResolver baseUrlResolver) {
        this.client = client;
        this.baseUrlResolver = baseUrlResolver;
    }

    /**
     * Одним файлом: в sitemap до 50 000 адресов, облигаций на бирже - несколько тысяч.
     * invest-api не ответил - {@code 503} с {@code Retry-After}, как у SEO-страниц: пустой sitemap с {@code 200}
     * поисковик понял бы как «страниц больше нет».
     */
    @GetMapping(SITEMAP_PATH)
    public ResponseEntity<String> sitemap(final HttpServletRequest request) {
        final String baseUrl = baseUrlResolver.resolve(request);
        final SeoModels.Sitemap sitemap;
        try {
            sitemap = client.getSitemap();
        } catch (final RestClientException exception) {
            log.warn("sitemap.xml is not available, invest-api failed: {}", exception.getMessage());
            return serviceUnavailable();
        }
        if (sitemap == null) {
            return serviceUnavailable();
        }
        final StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        final Consumer<String> url = path -> xml.append("  <url><loc>").append(Html.escape(baseUrl + path)).append("</loc></url>\n");

        url.accept("/bonds");
        orEmpty(sitemap.collections()).forEach(slug -> url.accept("/collections/" + Html.pathSegment(slug)));
        orEmpty(sitemap.tickers()).forEach(ticker -> url.accept("/bonds/" + Html.pathSegment(ticker)));
        orEmpty(sitemap.issuerIds()).stream().filter(Objects::nonNull).forEach(id -> url.accept("/issuers/" + id));
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(XML_UTF8).body(xml.toString());
    }

    /**
     * Личные разделы и вход закрыты. {@code /api/bonds} открыт: поисковик, исполняя SPA, запрашивает данные оттуда -
     * без них отрисованная страница была бы пустой.
     */
    @GetMapping(ROBOTS_PATH)
    public ResponseEntity<String> robots(final HttpServletRequest request) {
        final String body = """
                User-agent: *
                Allow: /
                Disallow: /portfolio
                Disallow: /alerts
                Disallow: /api/portfolio
                Disallow: /api/alerts
                Disallow: /api/calendar
                Disallow: /api/session
                Disallow: /oauth2/
                Disallow: /login
                Disallow: /logout

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrlResolver.resolve(request));
        return ResponseEntity.ok().contentType(TEXT_UTF8).body(body);
    }

    private static ResponseEntity<String> serviceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, SeoPageController.RETRY_AFTER_SECONDS)
                .build();
    }

    private static <T> List<T> orEmpty(final List<T> list) {
        return list == null ? List.of() : list;
    }
}
