package ADG.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── GET /login ───────────────────────────────────────────────────────

    @Test
    void getLoginRedirectsToLoginHtml() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void loginRedirectIsTemporary() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isFound());
    }

    // ── POST /logout ─────────────────────────────────────────────────────
    // The lobby's Logout button POSTs to /logout (POST-only, to prevent logout CSRF).

    @Test
    void postLogoutFromBrowserRedirectsHome() throws Exception {
        // A browser form/navigation (Accept: text/html) gets the redirect to the lobby.
        mockMvc.perform(post("/logout").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void postLogoutFromXhrReturnsNoContent() throws Exception {
        // The actual button call is an XHR (Accept: application/json) → 204, then the
        // client reloads the lobby itself.
        mockMvc.perform(post("/logout").header("Accept", "application/json"))
                .andExpect(status().isNoContent());
    }
}