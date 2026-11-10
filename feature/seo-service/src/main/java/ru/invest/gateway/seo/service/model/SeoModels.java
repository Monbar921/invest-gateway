package ru.invest.gateway.seo.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ответы invest-api, нужные SEO-страницам: только используемые поля, остальные игнорируются - gateway не зависит
 * от модулей invest-api и не ломается от новых полей.
 */
public final class SeoModels {
    private SeoModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(BigDecimal quantity, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(Money nominal, Money current, BigDecimal percentagePrice, Money aci) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coupon(Integer quantityPerYear, BigDecimal interest, Boolean isFixed, BigDecimal yieldToMaturity,
                         BigDecimal yieldToOffer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuerRef(Long id, String shortTitle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bond(String ticker, String isin, String name, String sector, Price price, Coupon coupon,
                       String riskLevel, OffsetDateTime maturityDate, OffsetDateTime offerDate, boolean amortizationFlag,
                       IssuerRef issuer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issuer(Long id, String title, String shortTitle, String inn, List<Bond> bonds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record News(String title, String url, OffsetDateTime publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page<T>(List<T> content, long totalElements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Collection(String slug, String title, String description, Page<Bond> bonds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sitemap(List<String> tickers, List<Long> issuerIds, List<String> collections) {
    }
}
