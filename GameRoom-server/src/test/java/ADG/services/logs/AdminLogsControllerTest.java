package ADG.services.logs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminLogsControllerTest {

    // Matches the BCrypt hash in test/resources/application.yaml
    private static final String ADMIN_AUTH = basicAuth("admin", "test");

    @Autowired MockMvc mockMvc;

    @Test
    void logsReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/logs").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsReturns200WithAppsAndEntriesWhenAdmin() throws Exception {
        // Regression guard: the @RequestParam args must be explicitly named, or Spring
        // throws (no -parameters flag) and this returns 500 instead of 200.
        mockMvc.perform(get("/admin/logs")
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apps").isArray())
                .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    void logsAcceptsAllFilterParams() throws Exception {
        // Exercises every request parameter so the binding for each is covered.
        mockMvc.perform(get("/admin/logs")
                        .param("app", "gameroom")
                        .param("level", "info")
                        .param("since", "7d")
                        .param("http", "4xx,5xx")
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apps").isArray())
                .andExpect(jsonPath("$.entries").isArray());
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}