package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.invest.gateway.seo.service.model.SeoModels;
import ru.invest.gateway.seo.service.render.SeoPage;
import ru.invest.gateway.seo.service.render.SeoPageBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Метаданные и содержимое SEO-страниц из ответов invest-api: неполные данные, адреса, длина description.
 */
class SeoPageBuilderTest {
    private static final OffsetDateTime MATURITY = OffsetDateTime.parse("2030-03-01T00:00:00Z");

    private final SeoPageBuilder builder = new SeoPageBuilder("Облигации");

    @Test
    void floaterWithoutYieldAndPriceHasNoSuchFacts() {
        final SeoPage page = builder.bond(bond("RU000A10EHC1", null, null, null), List.of());

        // имени нет - вместо него тикер; цены и доходности нет - их нет ни в description, ни в содержимом
        assertThat(page.title()).isEqualTo("RU000A10EHC1 (RU000A10EHC1) - цена и доходность облигации - Облигации");
        assertThat(page.description()).isEqualTo("RU000A10EHC1. График цены, выплаты купонов и новости.");
        assertThat(page.bodyHtml())
                .contains("<h1>RU000A10EHC1</h1>")
                .doesNotContain("Цена")
                .doesNotContain("Доходность")
                .doesNotContain("ISIN")
                .doesNotContain("Новости")
                .doesNotContain("Эмитент");
    }

    @Test
    void tickerIsEncodedInPathsAndEscapedInText() {
        final SeoPage page = builder.bond(bond("A B/<C>", "ISIN1", "Имя", null), List.of());

        assertThat(page.path()).isEqualTo("/bonds/A%20B%2F%3CC%3E");
        assertThat(page.bodyHtml())
                .contains("<dd>A B/&lt;C&gt;</dd>")
                .contains("href=\"/calculator?ticker=A%20B%2F%3CC%3E\"")
                .doesNotContain("<C>");
    }

    @Test
    void bondWithOfferAndIssuerWithoutName() {
        final SeoModels.Bond bond = new SeoModels.Bond("T1", "ISIN1", "Облигация", null, null,
                new SeoModels.Coupon(4, new BigDecimal("12.5"), false, null, new BigDecimal("18.1")), "RISK_LEVEL_UNKNOWN",
                MATURITY, OffsetDateTime.parse("2027-06-01T21:00:00Z"), true, new SeoModels.IssuerRef(7L, null));

        final String body = builder.bond(bond, List.of()).bodyHtml();

        assertThat(body)
                // дата оферты - по Москве: 21:00 UTC - уже следующий день
                .contains("<dt>Доходность к оферте</dt><dd>18,1% годовых, оферта 02.06.2027</dd>")
                .contains("<dt>Купон</dt><dd>12,5% к цене, переменный, 4 выплат в год</dd>")
                .contains("<a href=\"/issuers/7\">Эмитент 7</a>")
                // неизвестный уровень риска не показывается
                .doesNotContain("Уровень риска");
    }

    @Test
    void newsWithoutTitleIsSkippedAndWithoutUrlIsPlainText() {
        final List<SeoModels.News> news = List.of(
                new SeoModels.News(null, "https://n.test/1", null),
                new SeoModels.News("Без ссылки", null, OffsetDateTime.parse("2026-11-01T10:00:00Z")),
                new SeoModels.News("Со ссылкой", "https://n.test/2?a=1&b=2", null));

        final String body = builder.bond(bond("T1", null, "Облигация", null), news).bodyHtml();

        assertThat(body)
                .contains("<li>01.11.2026 - Без ссылки</li>")
                .contains("<li><a href=\"https://n.test/2?a=1&amp;b=2\" rel=\"nofollow noopener\">Со ссылкой</a></li>")
                .doesNotContain("https://n.test/1");
    }

    @Test
    void newsLinkOnlyForHttpUrls() {
        // адрес новости - внешние данные: javascript: в href выполнился бы по клику до загрузки SPA
        final List<SeoModels.News> news = List.of(new SeoModels.News("Новость", "javascript:alert(1)", null));

        final String body = builder.bond(bond("T1", null, "Облигация", null), news).bodyHtml();

        assertThat(body).contains("<li>Новость</li>").doesNotContain("javascript:");
    }

    @Test
    void issuerWithoutTitlesIsNamedById() {
        final SeoPage page = builder.issuer(new SeoModels.Issuer(42L, null, null, null, null), List.of());

        assertThat(page.title()).isEqualTo("Эмитент 42 - облигации эмитента - Облигации");
        assertThat(page.description()).startsWith("Облигации эмитента Эмитент 42: выпусков в обращении - 0.");
        assertThat(page.path()).isEqualTo("/issuers/42");
        assertThat(page.bodyHtml())
                .contains("<h1>Эмитент 42</h1>")
                .contains("<p>Выпусков в обращении нет.</p>")
                .doesNotContain("ИНН");
    }

    @Test
    void issuerWithFullTitleOnlyDoesNotRepeatIt() {
        final SeoPage page = builder.issuer(new SeoModels.Issuer(42L, "АО \"Тест\"", null, "123", List.of()), List.of());

        assertThat(page.bodyHtml())
                .contains("<h1>АО &quot;Тест&quot;</h1>")
                .doesNotContain("<p>АО &quot;Тест&quot;</p>")
                .contains("<dt>ИНН</dt><dd>123</dd>");
    }

    @Test
    void emptyCollectionHasNoTableAndNoShownOfTotal() {
        final SeoPage withoutPage = builder.collection(new SeoModels.Collection("floaters", "Флоатеры", "Описание", null));
        final SeoPage emptyPage = builder.collection(new SeoModels.Collection("floaters", "Флоатеры", "Описание",
                new SeoModels.Page<>(List.of(), 0)));

        for (final SeoPage page : List.of(withoutPage, emptyPage)) {
            assertThat(page.status()).isEqualTo(HttpStatus.OK);
            assertThat(page.bodyHtml())
                    .contains("<p>Выпусков в обращении нет.</p>")
                    .doesNotContain("<table>")
                    .doesNotContain("Показаны первые");
        }
    }

    @Test
    void longDescriptionIsCutTo300Characters() {
        final String longText = "Слово ".repeat(100);
        final SeoPage page = builder.collection(new SeoModels.Collection("x", "X", longText, null));

        assertThat(page.description()).hasSizeLessThanOrEqualTo(300).endsWith("…").startsWith("Слово Слово");
        assertThat(page.description()).doesNotContain(" …");
        // в содержимом - полный текст
        assertThat(page.bodyHtml()).contains(longText.strip());
    }

    @Test
    void bondListWithoutCollections() {
        final SeoPage page = builder.bondList(List.of());

        assertThat(page.path()).isEqualTo("/bonds");
        assertThat(page.bodyHtml()).contains("<h1>Облигации</h1>").doesNotContain("Подборки");
    }

    private static SeoModels.Bond bond(final String ticker, final String isin, final String name,
                                       final SeoModels.Coupon coupon) {
        return new SeoModels.Bond(ticker, isin, name, null, null, coupon, null, null, null, false, null);
    }
}
