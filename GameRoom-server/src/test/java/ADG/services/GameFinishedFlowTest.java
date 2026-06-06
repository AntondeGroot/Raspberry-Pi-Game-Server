package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.Lobby.RoomServiceException;
import ADG.config.GamesConfig;
import ADG.config.SpriteSheetsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Integration test for the full leave-and-restart flow.
 *
 * Scenario tested:
 *  1. Room is in WAITING — player selects Qwixx and starts a game.
 *  2. Room transitions to PLAYING.
 *  3. All players leave Qwixx — Qwixx POSTs /rooms/{roomId}/game-finished.
 *  4. Room resets to WAITING and the game session ID is cleared.
 *  5. Player can start a second game immediately.
 *
 * Qwixx's HTTP responses are mocked to match the shapes defined in its OpenAPI contract:
 *   POST /games           → { "sessionId": "..." }   (CreateNewGame201Response)
 *   POST /games/{id}/players → { "playerId": "...", "sessionId": "..." }
 *   POST /games/{id}      → 200 OK (void)
 */
@ExtendWith(MockitoExtension.class)
class GameFinishedFlowTest {

    private RoomServiceImpl service;
    private GameFinishedCallbackController callbackController;
    private RoomStore roomStore;

    @Mock private GamesConfig gamesConfig;
    @Mock private SpriteSheetsConfig spriteSheetsConfig;
    @Mock private RestTemplate restTemplate;
    @Mock private RoomSseRegistry sseRegistry;
    @Mock private LobbySseRegistry lobbySseRegistry;

    @BeforeEach
    void setUp() throws Exception {
        roomStore = new RoomStore();

        service = spy(new RoomServiceImpl());
        inject(service, "gamesConfig",        gamesConfig);
        inject(service, "spriteSheetsConfig", spriteSheetsConfig);
        inject(service, "roomStore",          roomStore);
        inject(service, "restTemplate",       restTemplate);
        inject(service, "sseRegistry",        sseRegistry);
        inject(service, "lobbySseRegistry",   lobbySseRegistry);
        inject(service, "serverPort",         4100);

        callbackController = new GameFinishedCallbackController();
        inject(callbackController, "roomStore",        roomStore);
        inject(callbackController, "sseRegistry",      sseRegistry);
        inject(callbackController, "lobbySseRegistry", lobbySseRegistry);

        lenient().when(spriteSheetsConfig.getSheets()).thenReturn(List.of());
        lenient().doReturn("player-1").when(service).getPlayerIdFromRequest();
    }

    // ── full flow ─────────────────────────────────────────────────────────────

    @Test
    void startGame_gameFinishedCallback_resetsRoomToWaiting() throws RoomServiceException {
        Room room = publishedRoomWithPlayer("qwixx", "player-1", "Alice");
        stubQwixx("session-1");

        service.startGame(room.getId());

        assertEquals(GameStatus.PLAYING, room.getStatus(), "room must be PLAYING after startGame");
        assertEquals("session-1", room.getGameSessionId());

        // Qwixx fires the callback when all players have left
        callbackController.gameFinished(room.getId());

        assertEquals(GameStatus.WAITING, room.getStatus(), "room must reset to WAITING after callback");
        assertNull(room.getGameSessionId(), "session ID must be cleared after callback");
    }

    @Test
    void afterGameFinishedCallback_aNewGameCanBeStarted() throws RoomServiceException {
        Room room = publishedRoomWithPlayer("qwixx", "player-1", "Alice");

        // Round 1
        stubQwixx("session-1");
        service.startGame(room.getId());
        assertEquals(GameStatus.PLAYING, room.getStatus());

        callbackController.gameFinished(room.getId());
        assertEquals(GameStatus.WAITING, room.getStatus());

        // Round 2 — a fresh session ID per OpenAPI contract
        stubQwixx("session-2");
        service.startGame(room.getId());

        assertEquals(GameStatus.PLAYING, room.getStatus(), "room must be PLAYING for the second game");
        assertEquals("session-2", room.getGameSessionId(), "second game must use the new session ID");
    }

    @Test
    void gameFinishedCallback_unknownRoomId_isIgnoredGracefully() {
        // Must not throw — Qwixx may occasionally fire the callback after a room was deleted
        callbackController.gameFinished("no-such-room");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Room publishedRoomWithPlayer(String gameId, String playerId, String name)
            throws RoomServiceException {
        GameDefinition def = new GameDefinition();
        def.setId(gameId);
        def.setBaseUrl("http://game-server");
        def.setMinPlayers(1);
        def.setMaxPlayers(5);
        lenient().when(gamesConfig.findById(gameId)).thenReturn(Optional.of(def));

        Room room = new Room();
        room.setId(UUID.randomUUID().toString());
        room.setName("Test Room");
        room.setCreatedByUserId(playerId);
        room.setStatus(GameStatus.WAITING);
        room.setGameId(gameId);
        room.addPlayer(playerId);
        room.addPlayerName(playerId, name);
        service.createRoom(room);
        service.publishRoom(room.getId());
        return room;
    }

    /**
     * Stubs the Qwixx HTTP calls that GameRoom makes during startGame,
     * using response shapes from Qwixx's OpenAPI contract.
     */
    @SuppressWarnings("unchecked")
    private void stubQwixx(String sessionId) {
        // POST /games → { "sessionId": "..." }  (CreateNewGame 201 body)
        when(restTemplate.postForObject(endsWith("/games"), any(), eq(Map.class)))
                .thenReturn(Map.of("sessionId", sessionId));
        // POST /games/{id}/players → { "playerId": "...", "sessionId": "..." }
        lenient().when(restTemplate.postForObject(endsWith("/players"), any(), eq(Map.class)))
                .thenReturn(Map.of("playerId", "player-1", "sessionId", sessionId));
        // POST /games/{id} (start) → void; default null from Mockito is fine
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found on " + target.getClass().getName());
    }
}