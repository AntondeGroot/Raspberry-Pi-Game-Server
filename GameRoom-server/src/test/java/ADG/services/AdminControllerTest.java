package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminControllerTest {

    // Matches the BCrypt hash in test/resources/application.yaml
    private static final String ADMIN_AUTH = basicAuth("admin", "test");

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;

    @BeforeEach
    void clearRooms() {
        roomStore.rooms.clear();
        roomStore.inactiveSince.clear();
    }

    // ── GET /admin/check ─────────────────────────────────────────────────────

    @Test
    void checkAdminReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/check")
                        .header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkAdminReturns200WhenAuthenticatedAsAdmin() throws Exception {
        mockMvc.perform(get("/admin/check")
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }

    // ── DELETE /admin/rooms/{id} ─────────────────────────────────────────────

    @Test
    void deleteRoomReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/admin/rooms/some-room-id")
                        .header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRoomReturns204WhenAuthenticatedAsAdmin() throws Exception {
        Room room = buildRoom("Alpha");
        roomStore.rooms.add(room);

        mockMvc.perform(delete("/admin/rooms/" + room.getId())
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRoomActuallyRemovesItFromStore() throws Exception {
        Room room = buildRoom("Beta");
        roomStore.rooms.add(room);

        mockMvc.perform(delete("/admin/rooms/" + room.getId())
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isNoContent());

        assertNull(roomStore.rooms.stream().filter(r -> r.getId().equals(room.getId())).findFirst().orElse(null),
                "room must be gone from the store after admin delete");
    }

    @Test
    void deleteNonExistentRoomReturns204WithoutError() throws Exception {
        // Deleting an unknown room must be idempotent — no 404/500.
        mockMvc.perform(delete("/admin/rooms/does-not-exist")
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRoomDoesNotAffectOtherRooms() throws Exception {
        Room roomA = buildRoom("Alpha");
        Room roomB = buildRoom("Beta");
        roomStore.rooms.add(roomA);
        roomStore.rooms.add(roomB);

        mockMvc.perform(delete("/admin/rooms/" + roomA.getId())
                        .header("Accept", "application/json")
                        .header("Authorization", ADMIN_AUTH))
                .andExpect(status().isNoContent());

        assertNull(roomStore.rooms.stream().filter(r -> r.getId().equals(roomA.getId())).findFirst().orElse(null),
                "deleted room must be gone");
        assertNotNull(roomStore.rooms.stream().filter(r -> r.getId().equals(roomB.getId())).findFirst().orElse(null),
                "other room must be unaffected");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    private Room buildRoom(String name) {
        Room r = new Room();
        r.setId(java.util.UUID.randomUUID().toString());
        r.setName(name);
        r.setCreatedByUserId("creator-1");
        r.setStatus(GameStatus.WAITING);
        r.setGameId("keezen");
        return r;
    }
}