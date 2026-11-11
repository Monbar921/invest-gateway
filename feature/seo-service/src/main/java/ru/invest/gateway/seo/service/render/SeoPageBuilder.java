package ru.invest.gateway.seo.service.render;

import org.springframework.http.HttpStatus;
import ru.invest.gateway.seo.service.model.SeoModels;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static ru.invest.gateway.seo.service.render.Html.escape;
import static ru.invest.gateway.seo.service.render.Html.pathSegment;

/**
 * Метаданные и содержимое SEO-страниц. Текст только описывает облигацию (цена, доходность, даты) - без оценок и
 * советов. Чего нет в данных, того нет и на странице: значение не придумывается.
 */
public class SeoPageBuilder {
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", RU);
    private static final String DASH = " - ";
    private static final String OG_WEBSITE = "website";
    private static final String OG_ARTICLE = "article";
    private static final int DESCRIPTION_LIMIT = 300;

    private final String siteName;

    public SeoPageBuilder(final String siteName) {
        this.siteName = siteName;
    }

    public SeoPage bond(final SeoModels.Bond bond, final List<SeoModels.News> news) {
        final String name = Objects.requireNonNullElse(bond.name(), bond.ticker());
        final String title = name + " (" + bond.ticker() + ") - цена и доходность облигации" + DASH + siteName;

        final List<String> facts = new ArrayList<>();
        facts.add(name + (bond.isin() == null ? "" : ", ISIN " + bond.isin()));
        if (percentagePrice(bond) != null) {
            facts.add("цена " + percent(percentagePrice(bond)) + " от номинала");
        }
        if (yieldToMaturity(bond) != null) {
            facts.add("доходность к погашению " + percent(yieldToMaturity(bond)) + " годовых");
        }
        if (bond.coupon() != null && bond.coupon().interest() != null) {
            facts.add("купонная доходность " + percent(bond.coupon().interest()));
        }
        if (bond.maturityDate() != null) {
            facts.add("погашение " + date(bond.maturityDate()));
        }
        final String description = limit(String.join(", ", facts) + ". График цены, выплаты купонов и новости.");

        final StringBuilder body = new StringBuilder()
                .append(breadcrumbs(crumb("/bonds", "Облигации"), crumb(null, bond.ticker())))
                .append("<h1>").append(escape(name)).append("</h1>\n")
                .append("<dl>\n")
                .append(term("Тикер", escape(bond.ticker())))
                .append(term("ISIN", escape(bond.isin())))
                .append(term("Цена", priceText(bond)))
                .append(term("Доходность к погашению", yieldToMaturity(bond) == null ? null : percent(yieldToMaturity(bond)) + " годовых"))
                .append(term("Доходность к оферте", offerText(bond)))
                .append(term("Купон", couponText(bond.coupon())))
                .append(term("НКД", bond.price() == null ? null : money(bond.price().aci())))
                .append(term("Номинал", nominalText(bond)))
                .append(term("Погашение", bond.maturityDate() == null ? null : date(bond.maturityDate())))
                .append(term("Уровень риска", risk(bond.riskLevel())))
                .append(term("Эмитент", issuerLink(bond.issuer())))
                .append("</dl>\n")
                .append(newsList(news))
                .append("<p><a href=\"/calculator?ticker=").append(escape(pathSegment(bond.ticker())))
                .append("\">Калькулятор дохода по облигации</a></p>\n");

        return new SeoPage(HttpStatus.OK, title, description, "/bonds/" + pathSegment(bond.ticker()), OG_ARTICLE, true,
                main(body));
    }

    public SeoPage issuer(final SeoModels.Issuer issuer, final List<SeoModels.News> news) {
        final String name = issuerName(issuer);
        final List<SeoModels.Bond> bonds = Objects.requireNonNullElse(issuer.bonds(), List.of());
        final String title = name + " - облигации эмитента" + DASH + siteName;
        final String description = limit("Облигации эмитента " + name
                + (issuer.inn() == null ? "" : " (ИНН " + issuer.inn() + ")")
                + ": выпусков в обращении - " + bonds.size()
                + ". Цены, доходности к погашению, даты погашения и новости эмитента.");

        final StringBuilder body = new StringBuilder()
                .append(breadcrumbs(crumb("/bonds", "Облигации"), crumb(null, name)))
                .append("<h1>").append(escape(name)).append("</h1>\n");
        if (issuer.title() != null && !issuer.title().equals(name)) {
            body.append("<p>").append(escape(issuer.title())).append("</p>\n");
        }
        body.append("<dl>\n").append(term("ИНН", escape(issuer.inn()))).append("</dl>\n")
                .append("<h2>Выпуски</h2>\n")
                .append(bondTable(bonds))
                .append(newsList(news));

        return new SeoPage(HttpStatus.OK, title, description, "/issuers/" + issuer.id(), OG_WEBSITE, true, main(body));
    }

    public SeoPage collection(final SeoModels.Collection collection) {
        final List<SeoModels.Bond> bonds = collection.bonds() == null || collection.bonds().content() == null
                ? List.of() : collection.bonds().content();
        final String title = collection.title() + DASH + siteName;
        final StringBuilder body = new StringBuilder()
                .append(breadcrumbs(crumb("/bonds", "Облигации"), crumb(null, collection.title())))
                .append("<h1>").append(escape(collection.title())).append("</h1>\n")
                .append("<p>").append(escape(collection.description())).append("</p>\n")
                .append(bondTable(bonds));
        if (collection.bonds() != null && collection.bonds().totalElements() > bonds.size()) {
            body.append("<p>Показаны первые ").append(bonds.size()).append(" из ")
                    .append(collection.bonds().totalElements()).append(".</p>\n");
        }
        return new SeoPage(HttpStatus.OK, title, limit(collection.description()),
                "/collections/" + pathSegment(collection.slug()), OG_WEBSITE, true, main(body));
    }

    public SeoPage bondList(final List<SeoModels.Collection> collections) {
        final String title = "Облигации Мосбиржи: цены, доходность, купоны" + DASH + siteName;
        final String description = "Список облигаций: ОФЗ, корпоративные и валютные выпуски. Фильтры по цене, риску и "
                + "типу купона, сортировка по доходности к погашению и к оферте, лента новостей ЦБ и Мосбиржи.";
        final StringBuilder body = new StringBuilder()
                .append("<h1>Облигации</h1>\n")
                .append("<p>").append(escape(description)).append("</p>\n");
        if (!collections.isEmpty()) {
            body.append("<h2>Подборки</h2>\n<ul>\n");
            collections.forEach(collection -> body.append("<li><a href=\"/collections/")
                    .append(escape(pathSegment(collection.slug()))).append("\">")
                    .append(escape(collection.title())).append("</a></li>\n"));
            body.append("</ul>\n");
        }
        return new SeoPage(HttpStatus.OK, title, description, "/bonds", OG_WEBSITE, true, main(body));
    }

    public SeoPage notFound(final String what) {
        return new SeoPage(HttpStatus.NOT_FOUND, "Страница не найдена" + DASH + siteName, what + " не найдена.",
                null, OG_WEBSITE, false,
                main(new StringBuilder("<h1>Страница не найдена</h1>\n<p>").append(escape(what))
                        .append(" не найдена.</p>\n<p><a href=\"/bonds\">Все облигации</a></p>\n")));
    }

    private static String main(final StringBuilder body) {
        return "<main>\n" + body + "</main>";
    }

    private String bondTable(final List<SeoModels.Bond> bonds) {
        if (bonds.isEmpty()) {
            return "<p>Выпусков в обращении нет.</p>\n";
        }
        final String rows = bonds.stream()
                .map(bond -> "<tr><td><a href=\"/bonds/" + escape(pathSegment(bond.ticker())) + "\">"
                        + escape(Objects.requireNonNullElse(bond.name(), bond.ticker())) + "</a></td>"
                        + "<td>" + escape(bond.ticker()) + "</td>"
                        + "<td>" + orDash(percentagePrice(bond) == null ? null : percent(percentagePrice(bond))) + "</td>"
                        + "<td>" + orDash(yieldToMaturity(bond) == null ? null : percent(yieldToMaturity(bond))) + "</td>"
                        + "<td>" + orDash(bond.maturityDate() == null ? null : date(bond.maturityDate())) + "</td></tr>\n")
                .collect(Collectors.joining());
        return "<table>\n<thead><tr><th>Облигация</th><th>Тикер</th><th>Цена, % от номинала</th>"
                + "<th>Доходность к погашению, % годовых</th><th>Погашение</th></tr></thead>\n<tbody>\n"
                + rows + "</tbody>\n</table>\n";
    }

    private static String newsList(final List<SeoModels.News> news) {
        if (news == null || news.isEmpty()) {
            return "";
        }
        final String items = news.stream()
                .filter(item -> item.title() != null)
                .map(item -> "<li>" + (item.publishedAt() == null ? "" : date(item.publishedAt()) + " - ")
                        + (!isHttpUrl(item.url()) ? escape(item.title())
                        : "<a href=\"" + escape(item.url()) + "\" rel=\"nofollow noopener\">" + escape(item.title()) + "</a>")
                        + "</li>\n")
                .collect(Collectors.joining());
        return "<h2>Новости</h2>\n<ul>\n" + items + "</ul>\n";
    }

    // адрес новости - внешние данные: ссылка только на http(s), javascript: и прочие схемы - просто текст
    private static boolean isHttpUrl(final String url) {
        if (url == null) {
            return false;
        }
        final String lower = url.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String breadcrumbs(final String... crumbs) {
        return "<nav aria-label=\"Навигация\">" + String.join(" / ", crumbs) + "</nav>\n";
    }

    private static String crumb(final String href, final String text) {
        return href == null ? escape(text) : "<a href=\"" + escape(href) + "\">" + escape(text) + "</a>";
    }

    /**
     * @param valueHtml уже экранированное значение; null или пусто - строки нет
     */
    private static String term(final String name, final String valueHtml) {
        if (valueHtml == null || valueHtml.isEmpty()) {
            return "";
        }
        return "<dt>" + escape(name) + "</dt><dd>" + valueHtml + "</dd>\n";
    }

    private static String priceText(final SeoModels.Bond bond) {
        if (percentagePrice(bond) == null) {
            return null;
        }
        final String current = money(bond.price().current());
        return percent(percentagePrice(bond)) + " от номинала" + (current == null ? "" : " (" + current + ")");
    }

    private static String offerText(final SeoModels.Bond bond) {
        if (bond.offerDate() == null) {
            return null;
        }
        final BigDecimal yield = bond.coupon() == null ? null : bond.coupon().yieldToOffer();
        return (yield == null ? "" : percent(yield) + " годовых, ") + "оферта " + date(bond.offerDate());
    }

    private static String couponText(final SeoModels.Coupon coupon) {
        if (coupon == null) {
            return null;
        }
        final List<String> parts = new ArrayList<>();
        if (coupon.interest() != null) {
            parts.add(percent(coupon.interest()) + " к цене");
        }
        if (coupon.isFixed() != null) {
            parts.add(coupon.isFixed() ? "постоянный" : "переменный");
        }
        if (coupon.quantityPerYear() != null && coupon.quantityPerYear() > 0) {
            parts.add(coupon.quantityPerYear() + " выплат в год");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String nominalText(final SeoModels.Bond bond) {
        final String nominal = bond.price() == null ? null : money(bond.price().nominal());
        if (nominal == null) {
            return null;
        }
        return bond.amortizationFlag() ? nominal + ", с амортизацией" : nominal;
    }

    private static String issuerLink(final SeoModels.IssuerRef issuer) {
        if (issuer == null || issuer.id() == null) {
            return null;
        }
        final String name = Objects.requireNonNullElse(issuer.shortTitle(), "Эмитент " + issuer.id());
        return "<a href=\"/issuers/" + issuer.id() + "\">" + escape(name) + "</a>";
    }

    private static String issuerName(final SeoModels.Issuer issuer) {
        if (issuer.shortTitle() != null) {
            return issuer.shortTitle();
        }
        return Objects.requireNonNullElse(issuer.title(), "Эмитент " + issuer.id());
    }

    private static String risk(final String riskLevel) {
        if (riskLevel == null) {
            return null;
        }
        return switch (riskLevel) {
            case "RISK_LEVEL_LOW" -> "низкий";
            case "RISK_LEVEL_MODERATE" -> "средний";
            case "RISK_LEVEL_HIGH" -> "высокий";
            default -> null;
        };
    }

    private static BigDecimal percentagePrice(final SeoModels.Bond bond) {
        return bond.price() == null ? null : bond.price().percentagePrice();
    }

    private static BigDecimal yieldToMaturity(final SeoModels.Bond bond) {
        return bond.coupon() == null ? null : bond.coupon().yieldToMaturity();
    }

    private static String percent(final BigDecimal value) {
        return number(value) + "%";
    }

    private static String money(final SeoModels.Money money) {
        if (money == null || money.quantity() == null) {
            return null;
        }
        final String currency = money.currency() == null ? "" : money.currency().toUpperCase(Locale.ROOT);
        final String symbol = switch (currency) {
            case "RUB", "RUR" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "CNY" -> "¥";
            default -> currency;
        };
        return escape(number(money.quantity()) + (symbol.isEmpty() ? "" : " " + symbol));
    }

    private static String number(final BigDecimal value) {
        // DecimalFormat не потокобезопасен - новый на каждый вызов
        final DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(RU));
        return format.format(value);
    }

    private static String date(final OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(MOSCOW).format(DATE);
    }

    private static String orDash(final String value) {
        return value == null ? "-" : escape(value);
    }

    private static String limit(final String text) {
        if (text == null || text.length() <= DESCRIPTION_LIMIT) {
            return text;
        }
        return text.substring(0, DESCRIPTION_LIMIT - 1).stripTrailing() + "…";
    }
}
