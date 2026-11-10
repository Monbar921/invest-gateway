package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Лимит анонимных запросов по IP. Запросы - к /api/session: он отвечает сам, без invest-api.
 */
@SpringBootTest(properties = "invest.gateway.public-access.rate-limit.requests-per-minute=2")
@AutoConfigureMockMvc
@Import(KeycloakStubConfiguration.class)
class AnonymousRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRequestsOverLimitAreRejectedPerIp() throws Exception {
        mockMvc.perform(get("/api/session").with(ip("10.0.0.1"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/session").with(ip("10.0.0.1"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/session").with(ip("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));

        // у другого адреса свой запас
        mockMvc.perform(get("/api/session").with(ip("10.0.0.2"))).andExpect(status().isOk());
    }

    @Test
    void seoPagesShareTheLimit() throws Exception {
        // SEO-страница облигации показывает те же данные, что API, - выкачивать через неё так же нельзя
        mockMvc.perform(get("/robots.txt").with(ip("10.0.0.5"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/session").with(ip("10.0.0.5"))).andExpect(status().isOk());

        mockMvc.perform(get("/robots.txt").with(ip("10.0.0.5"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void loggedInUserIsNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/session").with(ip("10.0.0.3")).with(oauth2Login())).andExpect(status().isOk());
        }
    }

    @Test
    void nonApiRequestsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health").with(ip("10.0.0.4"))).andExpect(status().isOk());
        }
    }

    private static RequestPostProcessor ip(final String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
