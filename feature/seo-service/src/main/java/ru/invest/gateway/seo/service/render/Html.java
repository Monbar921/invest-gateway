package ru.invest.gateway.seo.service.render;

import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Экранирование: всё, что пришло из invest-api (названия, заголовки новостей), попадает в HTML только через него.
 */
public final class Html {
    private Html() {
    }

    /**
     * Текст и значение атрибута: экранируются {@code < > & " '}.
     */
    public static String escape(final String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    /**
     * Сегмент пути: тикер или slug в ссылке.
     */
    public static String pathSegment(final String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}
