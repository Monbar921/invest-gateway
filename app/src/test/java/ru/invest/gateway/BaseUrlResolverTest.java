package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.invest.gateway.seo.service.controller.BaseUrlResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Адрес сайта для canonical и sitemap: из настройки или из запроса, без завершающего «/» и без query.
 */
class BaseUrlResolverTest {

    @Test
    void publicUrlWinsAndLosesTrailingSlash() {
        final MockHttpServletRequest request = request();

        assertThat(new BaseUrlResolver(" https://bonds.example/ ").resolve(request)).isEqualTo("https://bonds.example");
        assertThat(new BaseUrlResolver("https://bonds.example").resolve(request)).isEqualTo("https://bonds.example");
    }

    @Test
    void blankPublicUrlTakesAddressFromRequestWithoutQuery() {
        final MockHttpServletRequest request = request();

        assertThat(new BaseUrlResolver(null).resolve(request)).isEqualTo("http://invest.localhost:8088");
        assertThat(new BaseUrlResolver("  ").resolve(request)).isEqualTo("http://invest.localhost:8088");
    }

    @Test
    void defaultPortIsOmitted() {
        final MockHttpServletRequest request = request();
        request.setScheme("https");
        request.setServerPort(443);

        assertThat(new BaseUrlResolver(null).resolve(request)).isEqualTo("https://invest.localhost");
    }

    private static MockHttpServletRequest request() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bonds/SU26238RMFS4");
        request.setServerName("invest.localhost");
        request.setServerPort(8088);
        request.setQueryString("page=2");
        return request;
    }
}
