package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void sessionIsStillPublic() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }
}
