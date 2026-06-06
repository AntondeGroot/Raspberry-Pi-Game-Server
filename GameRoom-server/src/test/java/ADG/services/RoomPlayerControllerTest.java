package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoomPlayerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;

    @BeforeEach
    void clearRooms() {
        roomStore.rooms.clear();
    }

    @Test
    void returns204AndRemovesPlayer() throws Exception {
        String playerId = UUID.randomUUID().toString();
        Room room = buildRoom(playerId);
        roomStore.rooms.add(room);

        mockMvc.perform(delete("/rooms/" + room.getId() + "/players/" + playerId)
                        .cookie(new jakarta.servlet.http.Cookie("playerid", playerId)))
                .andExpect(status().isNoContent());

        assertFalse(room.getPlayerIds().contains(playerId), "player must be removed from room");
    }

    @Test
    void returns403WhenCookieMismatch() throws Exception {
        String playerId = UUID.randomUUID().toString();
        Room room = buildRoom(playerId);
        roomStore.rooms.add(room);

        mockMvc.perform(delete("/rooms/" + room.getId() + "/players/" + playerId)
                        .cookie(new jakarta.servlet.http.Cookie("playerid", "someone-else")))
                .andExpect(status().isForbidden());

        assertTrue(room.getPlayerIds().contains(playerId), "player must still be in room");
    }

    @Test
    void returns403WhenNoCookie() throws Exception {
        String playerId = UUID.randomUUID().toString();
        Room room = buildRoom(playerId);
        roomStore.rooms.add(room);

        mockMvc.perform(delete("/rooms/" + room.getId() + "/players/" + playerId))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns404WhenRoomNotFound() throws Exception {
        String playerId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/rooms/no-such-room/players/" + playerId)
                        .cookie(new jakarta.servlet.http.Cookie("playerid", playerId)))
                .andExpect(status().isNotFound());
    }

    private Room buildRoom(String playerId) {
        Room r = new Room();
        r.setId(UUID.randomUUID().toString());
        r.setName("Test Room");
        r.setCreatedByUserId(playerId);
        r.setStatus(GameStatus.PLAYING);
        r.addPlayer(playerId);
        r.addPlayerName(playerId, "TestPlayer");
        return r;
    }
}