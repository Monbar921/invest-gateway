package ru.invest.gateway.seo.service.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import ru.invest.gateway.seo.service.client.InvestApiSeoClient;
import ru.invest.gateway.seo.service.config.SeoProperties;
import ru.invest.gateway.seo.service.model.SeoModels;
import ru.invest.gateway.seo.service.render.HtmlPageRenderer;
import ru.invest.gateway.seo.service.render.SeoPage;
import ru.invest.gateway.seo.service.render.SeoPageBuilder;
import ru.invest.gateway.seo.service.render.UiTemplateProvider;

import java.util.List;
import java.util.function.Supplier;

/**
 * Страницы для поисковиков и ссылок: тот же {@code index.html}, что отдаёт UI, но с title, description, canonical,
 * Open Graph и готовым содержимым в {@code #root}. Браузер грузит SPA как обычно, React заменяет содержимое.
 * <p>
 * invest-api не ответил - оболочка без содержимого с {@code 503} и {@code Retry-After}: человек всё равно получает
 * SPA, а поисковик не запоминает пустую страницу.
 */
@Slf4j
@RestController
@ConditionalOnBooleanProperty(name = {"invest.gateway.seo.enabled", "invest.gateway.public-access.enabled"},
        matchIfMissing = true)
public class SeoPageController {
    public static final String BONDS_PATH = "/bonds";
    public static final String BOND_PATH = "/bonds/{ticker}";
    public static final String ISSUER_PATH = "/issuers/{id}";
    public static final String COLLECTION_PATH = "/collections/{slug}";
    /** Через сколько секунд повторить запрос, если invest-api не ответил: страницы и sitemap.xml */
    static final String RETRY_AFTER_SECONDS = "60";
    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private final InvestApiSeoClient client;
    private final SeoPageBuilder builder;
    private final HtmlPageRenderer renderer;
    private final UiTemplateProvider templates;
    private final BaseUrlResolver baseUrlResolver;

    public SeoPageController(final InvestApiSeoClient client, final SeoProperties properties,
                             final UiTemplateProvider templates, final BaseUrlResolver baseUrlResolver) {
        this.client = client;
        this.builder = new SeoPageBuilder(properties.siteName());
        this.renderer = new HtmlPageRenderer(properties.siteName());
        this.templates = templates;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping(value = BONDS_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bonds(final HttpServletRequest request) {
        return page(request, () -> builder.bondList(client.getCollections()));
    }

    @GetMapping(value = BOND_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bond(@PathVariable final String ticker, final HttpServletRequest request) {
        return page(request, () -> client.getBond(ticker)
                .map(bond -> builder.bond(bond, optionalNews(() -> client.getBondNews(ticker))))
                .orElseGet(() -> builder.notFound("Облигация " + ticker)));
    }

    @GetMapping(value = ISSUER_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> issuer(@PathVariable final String id, final HttpServletRequest request) {
        final Long issuerId = parseId(id);
        if (issuerId == null) {
            return render(request, builder.notFound("Страница эмитента"));
        }
        return page(request, () -> client.getIssuer(issuerId)
                .map(issuer -> builder.issuer(issuer, optionalNews(() -> client.getIssuerNews(issuerId))))
                .orElseGet(() -> builder.notFound("Страница эмитента")));
    }

    @GetMapping(value = COLLECTION_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> collection(@PathVariable final String slug, final HttpServletRequest request) {
        return page(request, () -> client.getCollection(slug)
                .map(builder::collection)
                .orElseGet(() -> builder.notFound("Подборка")));
    }

    private ResponseEntity<String> page(final HttpServletRequest request, final Supplier<SeoPage> pageSupplier) {
        final SeoPage page;
        try {
            page = pageSupplier.get();
        } catch (final RestClientException exception) {
            log.warn("SEO page {} without content, invest-api failed: {}", request.getRequestURI(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .contentType(TEXT_HTML_UTF8)
                    .body(templates.getTemplate());
        }
        return render(request, page);
    }

    private ResponseEntity<String> render(final HttpServletRequest request, final SeoPage page) {
        return ResponseEntity.status(page.status())
                .contentType(TEXT_HTML_UTF8)
                .body(renderer.render(templates.getTemplate(), page, baseUrlResolver.resolve(request)));
    }

    // новости - дополнение: без них страница облигации всё равно полезна
    private List<SeoModels.News> optionalNews(final Supplier<SeoModels.Page<SeoModels.News>> news) {
        try {
            final SeoModels.Page<SeoModels.News> page = news.get();
            return page == null || page.content() == null ? List.of() : page.content();
        } catch (final RestClientException exception) {
            log.debug("SEO page without news: {}", exception.getMessage());
            return List.of();
        }
    }

    private static Long parseId(final String id) {
        try {
            return Long.parseLong(id);
        } catch (final NumberFormatException exception) {
            return null;
        }
    }
}
