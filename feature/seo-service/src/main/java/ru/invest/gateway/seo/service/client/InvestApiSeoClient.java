package ru.invest.gateway.seo.service.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.invest.gateway.seo.service.model.SeoModels;

import java.util.Optional;

/**
 * Чтение данных для SEO-страниц из invest-api ({@code /internal/rest/bonds/**}) - тех же, что UI получает через
 * {@code /api/bonds/**}. 404 - пустой Optional, остальные ошибки пробрасываются.
 */
public class InvestApiSeoClient {
    private static final int ISSUER_NEWS_SIZE = 10;
    private static final int COLLECTION_SIZE = 100;
    private static final int BOND_NEWS_SIZE = 5;

    private final RestClient restClient;

    public InvestApiSeoClient(final RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<SeoModels.Bond> getBond(final String ticker) {
        return notFoundAsEmpty(() -> restClient.get().uri("/bonds/{ticker}", ticker)
                .retrieve().body(SeoModels.Bond.class));
    }

    public SeoModels.Page<SeoModels.News> getBondNews(final String ticker) {
        return restClient.get().uri("/bonds/{ticker}/news?size={size}", ticker, BOND_NEWS_SIZE)
                .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public Optional<SeoModels.Issuer> getIssuer(final long id) {
        return notFoundAsEmpty(() -> restClient.get().uri("/bonds/issuers/{id}", id)
                .retrieve().body(SeoModels.Issuer.class));
    }

    public SeoModels.Page<SeoModels.News> getIssuerNews(final long id) {
        return restClient.get().uri("/bonds/issuers/{id}/news?size={size}", id, ISSUER_NEWS_SIZE)
                .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public Optional<SeoModels.Collection> getCollection(final String slug) {
        return notFoundAsEmpty(() -> restClient.get().uri("/bonds/collections/{slug}?size={size}", slug, COLLECTION_SIZE)
                .retrieve().body(SeoModels.Collection.class));
    }

    public java.util.List<SeoModels.Collection> getCollections() {
        return restClient.get().uri("/bonds/collections")
                .retrieve().body(new ParameterizedTypeReference<>() { });
    }

    public SeoModels.Sitemap getSitemap() {
        return restClient.get().uri("/bonds/sitemap").retrieve().body(SeoModels.Sitemap.class);
    }

    private static <T> Optional<T> notFoundAsEmpty(final java.util.function.Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (final HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
