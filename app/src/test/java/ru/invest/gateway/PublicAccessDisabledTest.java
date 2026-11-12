package ru.invest.gateway;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Публичный режим выключен: облигации, как раньше, только с сессией; состояние входа UI всё равно может узнать.
 */
@SpringBootTest(properties = "invest.gateway.public-access.enabled=false")
@AutoConfigureMockMvc
@Import(KeycloakStubConfiguration.class)
class PublicAccessDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bondsWithoutSessionReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bonds/RU000A0JX0J2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seoPagesRequireLogin() throws Exception {
        mockMvc.perform(get("/bonds/RU000A0JX0J2").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionIsStillPublic() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void calendarFeedIsOpenWithoutPublicMode() {
        // календарь по ссылке - не общие данные, публичный режим на него не влияет; invest-api в тесте недоступен
        assertThatThrownBy(() -> mockMvc.perform(get("/api/calendar/abc.ics")))
                .hasMessageContaining("http://127.0.0.1:1/internal/rest/calendar/abc.ics");
    }

    @Test
    void brokerClickWithoutSessionReturnsUnauthorized() throws Exception {
        // клик открыт только вместе с чтением облигаций
        mockMvc.perform(withCsrf(post("/api/bonds/RU000A0JX0J2/broker-links/alpha/click")))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder withCsrf(final MockHttpServletRequestBuilder request) throws Exception {
        final Cookie csrfCookie = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return request.cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue());
    }
}
