package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.invest.gateway.seo.service.render.HtmlPageRenderer;
import ru.invest.gateway.seo.service.render.SeoPage;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вставка метаданных и содержимого в оболочку UI: оболочки разного вида, без дублей тегов.
 */
class HtmlPageRendererTest {
    private static final String BASE_URL = "https://bonds.example";
    // как index.html UI: description в несколько строк, скрипт сессии в head
    private static final String UI_INDEX = """
            <!doctype html>
            <html lang="ru">
              <head>
                <meta charset="UTF-8" />
                <title>Облигации</title>
                <meta
                  name="description"
                  content="Облигации Мосбиржи: цены, доходность."
                />
                <script>window.investSession = null;</script>
              </head>
              <body>
                <div id="root"></div>
                <script type="module" src="/assets/index-abc.js"></script>
              </body>
            </html>
            """;
    private static final SeoPage PAGE = new SeoPage(HttpStatus.OK, "ОФЗ 26238", "Описание", "/bonds/SU26238RMFS4",
            "article", true, "<main><h1>ОФЗ 26238</h1></main>");

    private final HtmlPageRenderer renderer = new HtmlPageRenderer("Облигации");

    @Test
    void multilineDescriptionOfUiIsReplacedNotDuplicated() {
        final String html = renderer.render(UI_INDEX, PAGE, BASE_URL);

        assertThat(count(html, "name=\"description\"")).isEqualTo(1);
        assertThat(count(html, "<title>")).isEqualTo(1);
        assertThat(html)
                .doesNotContain("Облигации Мосбиржи: цены, доходность.")
                .contains("<meta name=\"description\" content=\"Описание\" />")
                .contains("<title>ОФЗ 26238</title>")
                .contains("<link rel=\"canonical\" href=\"https://bonds.example/bonds/SU26238RMFS4\" />")
                .contains("<meta property=\"og:url\" content=\"https://bonds.example/bonds/SU26238RMFS4\" />")
                // скрипты оболочки на месте
                .contains("<script>window.investSession = null;</script>")
                .contains("<div id=\"root\"><main><h1>ОФЗ 26238</h1></main></div>")
                .contains("<script type=\"module\" src=\"/assets/index-abc.js\"></script>");
    }

    @Test
    void templateWithoutRootGetsRootAfterBody() {
        final String html = renderer.render("<html><head><title>x</title></head><body class=\"app\"><p>заглушка</p></body></html>",
                PAGE, BASE_URL);

        assertThat(html).contains("<body class=\"app\"><div id=\"root\"><main><h1>ОФЗ 26238</h1></main></div><p>заглушка</p>");
        assertThat(count(html, "id=\"root\"")).isEqualTo(1);
    }

    @Test
    void rootWithContentIsFilledNotDuplicated() {
        // UiTemplateProvider принимает любую оболочку с id="root" - в #root может быть, например, noscript
        final String html = renderer.render(
                "<html><head></head><body><div id=\"root\"><noscript>Нужен JavaScript</noscript></div></body></html>",
                PAGE, BASE_URL);

        assertThat(count(html, "id=\"root\"")).isEqualTo(1);
        assertThat(html).contains("<main><h1>ОФЗ 26238</h1></main>");
    }

    @Test
    void replacementCharactersInTextAreLiteral() {
        // $ и \ в заголовке - не ссылки на группы regex
        final SeoPage page = new SeoPage(HttpStatus.OK, "Купон $1 \\ 5%", "$0 описание", "/bonds/X", "website", true,
                "<main>$1 \\g</main>");

        final String html = renderer.render(UI_INDEX, page, BASE_URL);

        assertThat(html)
                .contains("<title>Купон $1 \\ 5%</title>")
                .contains("content=\"$0 описание\"")
                .contains("<div id=\"root\"><main>$1 \\g</main></div>");
    }

    @Test
    void notIndexablePageWithoutPathHasNoCanonical() {
        final SeoPage page = new SeoPage(HttpStatus.NOT_FOUND, "Не найдена", "Нет", null, "website", false, "<main></main>");

        final String html = renderer.render(UI_INDEX, page, BASE_URL);

        assertThat(html)
                .contains("<meta name=\"robots\" content=\"noindex\" />")
                .doesNotContain("rel=\"canonical\"")
                .doesNotContain("og:url");
    }

    @Test
    void titleAndDescriptionAreEscaped() {
        final SeoPage page = new SeoPage(HttpStatus.OK, "<b>\"ПАО\"</b>", "a\" onload=\"x", "/issuers/1", "website", true, "");

        final String html = renderer.render(UI_INDEX, page, BASE_URL);

        assertThat(html)
                .contains("<title>&lt;b&gt;&quot;ПАО&quot;&lt;/b&gt;</title>")
                .contains("<meta name=\"description\" content=\"a&quot; onload=&quot;x\" />")
                .doesNotContain("<b>");
    }

    private static int count(final String text, final String part) {
        return (int) Pattern.compile(Pattern.quote(part)).matcher(text).results().count();
    }
}
