package ru.invest.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.invest.gateway.seo.service.render.UiTemplateProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Оболочка UI: держится ttl, при недоступном UI - последняя полученная, а если её не было - минимальная.
 */
class UiTemplateProviderTest {
    private static final String INDEX = "<html><head><title>x</title></head><body><div id=\"root\"></div></body></html>";
    private static final Duration TTL = Duration.ofMinutes(1);

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-10T10:00:00Z"));
    private MockRestServiceServer server;
    private UiTemplateProvider provider;

    @BeforeEach
    void setUp() {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://ui.test");
        server = MockRestServiceServer.bindTo(builder).build();
        final Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(final java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        provider = new UiTemplateProvider(builder.build(), TTL, clock);
    }

    @Test
    void templateIsCachedForTtl() {
        server.expect(once(), requestTo("http://ui.test/index.html")).andRespond(withSuccess(INDEX, MediaType.TEXT_HTML));

        assertThat(provider.getTemplate()).isEqualTo(INDEX);
        now.set(now.get().plusSeconds(59));
        assertThat(provider.getTemplate()).isEqualTo(INDEX);
        server.verify();
    }

    @Test
    void uiFailureKeepsLastTemplate() {
        server.expect(once(), requestTo("http://ui.test/index.html")).andRespond(withSuccess(INDEX, MediaType.TEXT_HTML));
        server.expect(once(), requestTo("http://ui.test/index.html")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        provider.getTemplate();
        now.set(now.get().plus(TTL));
        assertThat(provider.getTemplate()).isEqualTo(INDEX);
        server.verify();
    }

    @Test
    void withoutAnyTemplateFallbackShellIsUsed() {
        server.expect(once(), requestTo("http://ui.test/index.html")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(provider.getTemplate()).contains("<html lang=\"ru\">").contains("<div id=\"root\"></div>");
        // повторная попытка - только через ttl
        assertThat(provider.getTemplate()).contains("<div id=\"root\"></div>");
        server.verify();
    }

    @Test
    void pageWithoutRootIsNotAccepted() {
        server.expect(once(), requestTo("http://ui.test/index.html"))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThat(provider.getTemplate()).contains("<div id=\"root\"></div>");
    }
}
