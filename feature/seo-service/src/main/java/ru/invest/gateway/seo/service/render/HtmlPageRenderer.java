package ru.invest.gateway.seo.service.render;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.invest.gateway.seo.service.render.Html.escape;

/**
 * Вставляет метаданные и содержимое страницы в {@code index.html} UI: title, description, canonical, Open Graph -
 * в {@code <head>} (прежние title и description удаляются), содержимое - в {@code <div id="root">}.
 */
public class HtmlPageRenderer {
    private static final Pattern TITLE = Pattern.compile("<title>.*?</title>\\s*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DESCRIPTION = Pattern.compile(
            "<meta\\s+name=\"description\"[^>]*>\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAD_END = Pattern.compile("</head>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT = Pattern.compile("<div id=\"root\">\\s*</div>");
    private static final Pattern BODY_START = Pattern.compile("<body[^>]*>", Pattern.CASE_INSENSITIVE);

    private final String siteName;

    public HtmlPageRenderer(final String siteName) {
        this.siteName = siteName;
    }

    /**
     * @param baseUrl адрес сайта без завершающего «/» - для абсолютных canonical и og:url
     */
    public String render(final String template, final SeoPage page, final String baseUrl) {
        String html = TITLE.matcher(template).replaceFirst("");
        html = DESCRIPTION.matcher(html).replaceFirst("");
        html = HEAD_END.matcher(html).replaceFirst(Matcher.quoteReplacement(head(page, baseUrl) + "</head>"));

        final String root = "<div id=\"root\">" + page.bodyHtml() + "</div>";
        final Matcher rootMatcher = ROOT.matcher(html);
        if (rootMatcher.find()) {
            return rootMatcher.replaceFirst(Matcher.quoteReplacement(root));
        }
        return BODY_START.matcher(html).replaceFirst(match -> Matcher.quoteReplacement(match.group() + root));
    }

    private String head(final SeoPage page, final String baseUrl) {
        final StringBuilder head = new StringBuilder()
                .append("<title>").append(escape(page.title())).append("</title>\n")
                .append(meta("name", "description", page.description()));
        if (!page.indexable()) {
            head.append(meta("name", "robots", "noindex"));
        }
        if (page.path() != null) {
            final String url = baseUrl + page.path();
            head.append("<link rel=\"canonical\" href=\"").append(escape(url)).append("\" />\n")
                    .append(meta("property", "og:url", url));
        }
        return head
                .append(meta("property", "og:type", page.ogType()))
                .append(meta("property", "og:title", page.title()))
                .append(meta("property", "og:description", page.description()))
                .append(meta("property", "og:site_name", siteName))
                .append(meta("property", "og:locale", "ru_RU"))
                .append(meta("name", "twitter:card", "summary"))
                .toString();
    }

    private static String meta(final String attribute, final String name, final String content) {
        return "<meta " + attribute + "=\"" + name + "\" content=\"" + escape(content) + "\" />\n";
    }
}
