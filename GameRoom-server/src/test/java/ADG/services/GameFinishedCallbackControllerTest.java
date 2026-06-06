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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GameFinishedCallbackControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;

    @BeforeEach
    void setUp() {
        roomStore.rooms.clear();
    }

    @Test
    void returns204AndResetsRoomToWaiting() throws Exception {
        Room room = playingRoom();
        roomStore.rooms.add(room);

        mockMvc.perform(post("/rooms/" + room.getId() + "/game-finished"))
                .andExpect(status().isNoContent());

        assertEquals(GameStatus.WAITING, room.getStatus());
        assertNull(room.getGameSessionId());
    }

    @Test
    void returns204ForUnknownRoom() throws Exception {
        mockMvc.perform(post("/rooms/no-such-room/game-finished"))
                .andExpect(status().isNoContent());
    }

    private Room playingRoom() {
        Room r = new Room();
        r.setId(UUID.randomUUID().toString());
        r.setName("Test Room");
        r.setCreatedByUserId("player-1");
        r.setStatus(GameStatus.PLAYING);
        r.setGameSessionId("session-" + UUID.randomUUID());
        return r;
    }
}