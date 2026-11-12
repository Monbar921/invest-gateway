package ru.invest.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEO-страницы: готовый HTML из оболочки UI и данных invest-api, sitemap.xml и robots.txt. invest-api и UI заменяет
 * HTTP-заглушка: ответы по пути задаются в тесте, неизвестный путь - 404.
 */
@SpringBootTest(properties = {
    "invest.gateway.public-access.rate-limit.requests-per-minute=0",
    "invest.gateway.seo.template-ttl=0s",
    "server.forward-headers-strategy=framework"})
@AutoConfigureMockMvc
@Import(KeycloakStubConfiguration.class)
class SeoPagesTest {
    private static final String INDEX_HTML = """
            <!doctype html>
            <html lang="ru">
              <head>
                <meta charset="UTF-8" />
                <title>Облигации</title>
                <script type="module" crossorigin src="/assets/index-abc123.js"></script>
              </head>
              <body>
                <div id="root"></div>
              </body>
            </html>
            """;
    private static final String BOND = """
            {"ticker":"SU26238RMFS4","isin":"RU000A1038V6","name":"ОФЗ 26238","sector":"government",
             "price":{"nominal":{"quantity":1000,"currency":"rub"},"current":{"quantity":615.3,"currency":"rub"},
                      "percentagePrice":61.53,"aci":{"quantity":12.5,"currency":"rub"}},
             "coupon":{"quantityPerYear":2,"interest":11.46,"isFixed":true,"yieldToMaturity":14.82,"yieldToOffer":null},
             "riskLevel":"RISK_LEVEL_LOW","maturityDate":"2041-05-15T00:00:00Z","offerDate":null,
             "amortizationFlag":false,"issuer":{"id":42,"shortTitle":"Минфин России"},"newField":1}""";
    private static final String NEWS = """
            {"content":[{"title":"Аукцион <ОФЗ>","url":"https://moex.test/n/1","publishedAt":"2026-11-02T10:00:00Z"}],
             "totalElements":1}""";

    private static final Map<String, Response> RESPONSES = new ConcurrentHashMap<>();
    private static final HttpServer STUB = startStub();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void stubUrls(final DynamicPropertyRegistry registry) {
        final String url = "http://127.0.0.1:" + STUB.getAddress().getPort();
        registry.add("invest.gateway.invest-api-uri", () -> url);
        registry.add("invest.gateway.seo.ui-url", () -> url + "/ui");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @BeforeEach
    void setUp() {
        RESPONSES.clear();
        RESPONSES.put("/ui/index.html", new Response(200, INDEX_HTML));
    }

    @Test
    void bondPageWithoutSessionHasMetadataAndContent() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4/news", new Response(200, NEWS));

        final String html = mockMvc.perform(get("/bonds/SU26238RMFS4").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("<title>ОФЗ 26238 (SU26238RMFS4) - цена и доходность облигации - Облигации</title>")
                .doesNotContain("<title>Облигации</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost/bonds/SU26238RMFS4\" />")
                .contains("<meta property=\"og:title\" content=\"ОФЗ 26238 (SU26238RMFS4)")
                .contains("<meta property=\"og:type\" content=\"article\" />")
                .contains("<meta name=\"description\" content=\"ОФЗ 26238, ISIN RU000A1038V6, цена 61,53% от номинала, "
                        + "доходность к погашению 14,82% годовых, купонная доходность 11,46%, погашение 15.05.2041.")
                .doesNotContain("noindex")
                // оболочка UI сохраняется - браузер загрузит SPA
                .contains("<script type=\"module\" crossorigin src=\"/assets/index-abc123.js\"></script>")
                .contains("<div id=\"root\"><main>")
                .contains("<h1>ОФЗ 26238</h1>")
                .contains("<dt>Доходность к погашению</dt><dd>14,82% годовых</dd>")
                .contains("<dd>61,53% от номинала (615,3 ₽)</dd>")
                .contains("<a href=\"/issuers/42\">Минфин России</a>")
                // данных об оферте нет - строки нет, а не «—»
                .doesNotContain("Доходность к оферте")
                // заголовок новости экранирован
                .contains("Аукцион &lt;ОФЗ&gt;");
    }

    @Test
    void bondPageWithoutNewsStillRenders() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4/news", new Response(500, "{}"));

        final String html = body(get("/bonds/SU26238RMFS4"), 200);
        assertThat(html).contains("<h1>ОФЗ 26238</h1>").doesNotContain("<h2>Новости</h2>");
    }

    @Test
    void textFromApiIsEscaped() throws Exception {
        RESPONSES.put("/internal/rest/bonds/X1", new Response(200, """
                {"ticker":"X1","name":"<script>alert('x')</script>\\"","amortizationFlag":false}"""));

        final String html = body(get("/bonds/X1"), 200);
        assertThat(html)
                .doesNotContain("<script>alert")
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&quot;");
    }

    @Test
    void unknownBondIsNotFoundAndNotIndexed() throws Exception {
        final String html = body(get("/bonds/UNKNOWN"), 404);
        assertThat(html)
                .contains("<meta name=\"robots\" content=\"noindex\" />")
                .doesNotContain("rel=\"canonical\"")
                .contains("/assets/index-abc123.js");
    }

    @Test
    void investApiFailureGivesShellWithServiceUnavailable() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(500, "{}"));

        mockMvc.perform(get("/bonds/SU26238RMFS4"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().string(INDEX_HTML));
    }

    @Test
    void uiUnavailableKeepsLastTemplate() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));
        body(get("/bonds/SU26238RMFS4"), 200);
        RESPONSES.remove("/ui/index.html");

        // UI не ответил - последняя полученная оболочка, со ссылками на бандл
        final String html = body(get("/bonds/SU26238RMFS4"), 200);
        assertThat(html).contains("/assets/index-abc123.js").contains("<h1>ОФЗ 26238</h1>");
    }

    @Test
    void issuerPage() throws Exception {
        RESPONSES.put("/internal/rest/bonds/issuers/42", new Response(200, """
                {"id":42,"title":"Министерство финансов Российской Федерации","shortTitle":"Минфин России",
                 "inn":"7710168360","bonds":[%s]}""".formatted(BOND)));
        RESPONSES.put("/internal/rest/bonds/issuers/42/news", new Response(200, NEWS));

        final String html = body(get("/issuers/42"), 200);
        assertThat(html)
                .contains("<title>Минфин России - облигации эмитента - Облигации</title>")
                .contains("<link rel=\"canonical\" href=\"http://localhost/issuers/42\" />")
                .contains("(ИНН 7710168360): выпусков в обращении - 1.")
                .contains("<a href=\"/bonds/SU26238RMFS4\">ОФЗ 26238</a>")
                .contains("<td>14,82%</td>");
    }

    @Test
    void issuerWithNonNumericIdIsNotFound() throws Exception {
        body(get("/issuers/abc"), 404);
    }

    @Test
    void collectionPage() throws Exception {
        RESPONSES.put("/internal/rest/bonds/collections/ofz-top-yield", new Response(200, """
                {"slug":"ofz-top-yield","title":"Самые доходные ОФЗ","description":"ОФЗ по доходности к погашению.",
                 "bonds":{"content":[%s],"totalElements":120}}""".formatted(BOND)));

        final String html = body(get("/collections/ofz-top-yield"), 200);
        assertThat(html)
                .contains("<title>Самые доходные ОФЗ - Облигации</title>")
                .contains("<meta name=\"description\" content=\"ОФЗ по доходности к погашению.\" />")
                .contains("<link rel=\"canonical\" href=\"http://localhost/collections/ofz-top-yield\" />")
                .contains("<a href=\"/bonds/SU26238RMFS4\">ОФЗ 26238</a>")
                .contains("Показаны первые 1 из 120.");
    }

    @Test
    void bondListLinksCollectionsAndCanonicalDropsQuery() throws Exception {
        RESPONSES.put("/internal/rest/bonds/collections", new Response(200, """
                [{"slug":"floaters","title":"Флоатеры","description":"..."}]"""));

        final String html = body(get("/bonds").param("scope", "local"), 200);
        assertThat(html)
                .contains("<link rel=\"canonical\" href=\"http://localhost/bonds\" />")
                .contains("<a href=\"/collections/floaters\">Флоатеры</a>");
    }

    @Test
    void loggedInUserGetsTheSamePage() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));

        mockMvc.perform(get("/bonds/SU26238RMFS4").with(oauth2Login())).andExpect(status().isOk());
    }

    @Test
    void sitemapListsAllPages() throws Exception {
        RESPONSES.put("/internal/rest/bonds/sitemap", new Response(200, """
                {"tickers":["SU26238RMFS4","RU000A1080Y2"],"issuerIds":[42],"collections":["ofz-top-yield"]}"""));

        final String xml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml)
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
                .contains("<loc>http://localhost/bonds</loc>")
                .contains("<loc>http://localhost/collections/ofz-top-yield</loc>")
                .contains("<loc>http://localhost/bonds/SU26238RMFS4</loc>")
                .contains("<loc>http://localhost/bonds/RU000A1080Y2</loc>")
                .contains("<loc>http://localhost/issuers/42</loc>");
    }

    @Test
    void robotsClosesPrivatePagesAndPointsToSitemap() throws Exception {
        final String robots = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(robots)
                .contains("Disallow: /portfolio")
                .contains("Disallow: /api/alerts")
                .contains("Disallow: /api/calendar")
                .contains("Disallow: /plan")
                .contains("Disallow: /api/account")
                .doesNotContain("Disallow: /api/bonds")
                .contains("Sitemap: http://localhost/sitemap.xml");
    }

    @Test
    void otherPagesStillRequireLogin() throws Exception {
        mockMvc.perform(get("/portfolio").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/bonds/SU26238RMFS4/extra").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
    }

    @Test
    void headOfSeoPageWithoutSessionHasNoBody() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));

        mockMvc.perform(head("/bonds/SU26238RMFS4"))
                .andExpect(status().isOk())
                // тело у HEAD отбрасывает контейнер, MockMvc его оставляет - проверяются статус и тип
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mockMvc.perform(head("/robots.txt")).andExpect(status().isOk());
        mockMvc.perform(head("/issuers/abc")).andExpect(status().isNotFound());
    }

    @Test
    void canonicalAndSitemapUseForwardedAddress() throws Exception {
        RESPONSES.put("/internal/rest/bonds/SU26238RMFS4", new Response(200, BOND));
        RESPONSES.put("/internal/rest/bonds/sitemap", new Response(200, """
                {"tickers":["SU26238RMFS4"],"issuerIds":[],"collections":[]}"""));

        final String html = mockMvc.perform(get("/bonds/SU26238RMFS4")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "bonds.example")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        final String xml = mockMvc.perform(get("/sitemap.xml").header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "bonds.example"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("<link rel=\"canonical\" href=\"https://bonds.example/bonds/SU26238RMFS4\" />")
                .contains("<meta property=\"og:url\" content=\"https://bonds.example/bonds/SU26238RMFS4\" />");
        assertThat(xml).contains("<loc>https://bonds.example/bonds/SU26238RMFS4</loc>");
    }

    @Test
    void tickerIsEncodedForInvestApiAndInCanonical() throws Exception {
        // заглушка сравнивает декодированный путь: запрос в invest-api ушёл с тем же тикером
        RESPONSES.put("/internal/rest/bonds/A B", new Response(200, """
                {"ticker":"A B","name":"С пробелом","amortizationFlag":false}"""));

        final String html = body(get("/bonds/{ticker}", "A B"), 200);

        assertThat(html)
                .contains("<h1>С пробелом</h1>")
                .contains("<link rel=\"canonical\" href=\"http://localhost/bonds/A%20B\" />");
    }

    @Test
    void issuerWithoutTitlesAndBonds() throws Exception {
        RESPONSES.put("/internal/rest/bonds/issuers/7", new Response(200, """
                {"id":7,"title":null,"shortTitle":null,"inn":null,"bonds":[]}"""));

        final String html = body(get("/issuers/7"), 200);

        assertThat(html)
                .contains("<title>Эмитент 7 - облигации эмитента - Облигации</title>")
                .contains("<p>Выпусков в обращении нет.</p>")
                .doesNotContain("<h2>Новости</h2>");
    }

    @Test
    void issuerApiFailureGivesServiceUnavailable() throws Exception {
        RESPONSES.put("/internal/rest/bonds/issuers/7", new Response(500, "{}"));

        mockMvc.perform(get("/issuers/7"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void emptyCollectionRenders() throws Exception {
        RESPONSES.put("/internal/rest/bonds/collections/maturity-this-year", new Response(200, """
                {"slug":"maturity-this-year","title":"Погашение в этом году","description":"Описание",
                 "bonds":{"content":[],"totalElements":0}}"""));

        final String html = body(get("/collections/maturity-this-year"), 200);

        assertThat(html)
                .contains("<h1>Погашение в этом году</h1>")
                .contains("<p>Выпусков в обращении нет.</p>")
                .doesNotContain("Показаны первые");
    }

    @Test
    void unknownCollectionIsNotFound() throws Exception {
        final String html = body(get("/collections/unknown"), 404);

        assertThat(html).contains("<meta name=\"robots\" content=\"noindex\" />").doesNotContain("rel=\"canonical\"");
    }

    @Test
    void sitemapWithNullListsHasOnlyBondList() throws Exception {
        RESPONSES.put("/internal/rest/bonds/sitemap", new Response(200, """
                {"tickers":null,"issuerIds":[null,5],"collections":null}"""));

        final String xml = body(get("/sitemap.xml"), 200);

        assertThat(locations(xml)).containsExactly("http://localhost/bonds", "http://localhost/issuers/5");
    }

    @Test
    void sitemapIsWellFormedXmlWithEncodedPaths() throws Exception {
        RESPONSES.put("/internal/rest/bonds/sitemap", new Response(200, """
                {"tickers":["A&B","C D","Э1"],"issuerIds":[1],"collections":["ofz-top-yield"]}"""));

        final String xml = body(get("/sitemap.xml"), 200);

        assertThat(locations(xml)).containsExactly(
                "http://localhost/bonds",
                "http://localhost/collections/ofz-top-yield",
                "http://localhost/bonds/A&B",
                "http://localhost/bonds/C%20D",
                "http://localhost/bonds/%D0%AD1",
                "http://localhost/issuers/1");
    }

    @Test
    void sitemapWhenInvestApiFailsIsServiceUnavailable() throws Exception {
        RESPONSES.put("/internal/rest/bonds/sitemap", new Response(500, "{}"));

        // не пустой sitemap с 200: поисковик решил бы, что страниц больше нет. Как у SEO-страниц - 503 и Retry-After
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().string(""));
    }

    private static List<String> locations(final String xml) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        final var nodes = document.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "loc");
        final List<String> result = new java.util.ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            result.add(nodes.item(index).getTextContent());
        }
        return result;
    }

    private String body(final org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                        final int status) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(status))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static HttpServer startStub() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", SeoPagesTest::respond);
            server.start();
            return server;
        } catch (final IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(final HttpExchange exchange) throws IOException {
        final Response response = RESPONSES.getOrDefault(exchange.getRequestURI().getPath(), new Response(404, "{}"));
        final byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        final boolean html = exchange.getRequestURI().getPath().endsWith(".html");
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, html ? "text/html;charset=UTF-8" : "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record Response(int status, String body) {
    }
}
