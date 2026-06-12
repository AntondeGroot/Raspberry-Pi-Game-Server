package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.Lobby.RoomServiceException;
import ADG.config.GamesConfig;
import ADG.config.SpriteSheetsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    private RoomServiceImpl service;
    private RoomStore roomStore;

    @Mock
    private GamesConfig gamesConfig;

    @Mock
    private SpriteSheetsConfig spriteSheetsConfig;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RoomSseRegistry sseRegistry;

    @Mock
    private LobbySseRegistry lobbySseRegistry;

    @BeforeEach
    void setUp() throws Exception {
        roomStore = new RoomStore();
        // Use a spy so individual tests can stub getPlayerIdFromRequest() without
        // needing a live servlet container.
        service = spy(new RoomServiceImpl());
        // Inject mock GamesConfig and RoomStore — @PostConstruct is NOT invoked when newing
        // directly, so we start with a clean, empty room list for every test.
        Field gamesConfigField = RoomServiceImpl.class.getDeclaredField("gamesConfig");
        gamesConfigField.setAccessible(true);
        gamesConfigField.set(service, gamesConfig);

        Field roomStoreField = RoomServiceImpl.class.getDeclaredField("roomStore");
        roomStoreField.setAccessible(true);
        roomStoreField.set(service, roomStore);

        Field restTemplateField = RoomServiceImpl.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(service, restTemplate);

        Field sseRegistryField = RoomServiceImpl.class.getDeclaredField("sseRegistry");
        sseRegistryField.setAccessible(true);
        sseRegistryField.set(service, sseRegistry);

        Field lobbySseRegistryField = RoomServiceImpl.class.getDeclaredField("lobbySseRegistry");
        lobbySseRegistryField.setAccessible(true);
        lobbySseRegistryField.set(service, lobbySseRegistry);

        Field spriteSheetsConfigField = RoomServiceImpl.class.getDeclaredField("spriteSheetsConfig");
        spriteSheetsConfigField.setAccessible(true);
        spriteSheetsConfigField.set(service, spriteSheetsConfig);

        lenient().when(spriteSheetsConfig.getSheets()).thenReturn(List.of());

        lenient().when(gamesConfig.findById(anyString())).thenReturn(Optional.empty());
        // Default: caller is the room creator used by buildRoom().
        lenient().doReturn("creator-1").when(service).getPlayerIdFromRequest();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Room buildRoom(String name) {
        Room r = new Room();
        r.setId(java.util.UUID.randomUUID().toString());
        r.setName(name);
        r.setCreatedByUserId("creator-1");
        r.setStatus(GameStatus.WAITING);
        r.setGameId("keezen");
        return r;
    }

    // ── getRooms ─────────────────────────────────────────────────────────────

    @Test
    void getRoomsStartsEmpty() throws RoomServiceException {
        assertTrue(service.getRooms().isEmpty());
    }

    @Test
    void getRoomsHidesPendingRooms() throws RoomServiceException {
        service.createRoom(buildRoom("Alpha"));
        assertTrue(service.getRooms().isEmpty(), "PENDING room must not appear in lobby list");
    }

    @Test
    void getRoomsShowsPublishedRoom() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        assertEquals(1, service.getRooms().size());
        assertEquals(GameStatus.WAITING, service.getRooms().get(0).getStatus());
    }

    // ── createRoom ───────────────────────────────────────────────────────────

    @Test
    void createRoomAddsAndReturnsRoom() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        Room result = service.createRoom(room);

        assertSame(room, result);
        assertEquals(GameStatus.PENDING, result.getStatus(), "newly created room must be PENDING");
        assertTrue(service.getRooms().isEmpty(), "PENDING room must not be visible in lobby");
    }

    // ── publishRoom ──────────────────────────────────────────────────────────

    @Test
    void publishRoomMakesRoomVisible() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        assertEquals(1, service.getRooms().size());
    }

    @Test
    void publishRoomSetsStatusToWaiting() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        assertEquals(GameStatus.WAITING, service.getRooms().get(0).getStatus());
    }

    @Test
    void publishRoomOnNonPendingRoomIsNoOp() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        // calling again should not break anything
        service.publishRoom(room.getId());
        assertEquals(GameStatus.WAITING, service.getRooms().get(0).getStatus());
    }

    @Test
    void publishRoomOnUnknownIdIsNoOp() throws RoomServiceException {
        service.publishRoom("non-existent-id"); // must not throw
        assertTrue(service.getRooms().isEmpty());
    }

    @Test
    void createRoomWithBlankNameThrows() throws RoomServiceException {
        Room room = buildRoom("   ");
        assertThrows(RoomServiceException.class, () -> service.createRoom(room));
        assertTrue(service.getRooms().isEmpty());
    }

    @Test
    void createRoomDuplicateThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        assertThrows(RoomServiceException.class, () -> service.createRoom(room));
        assertNotNull(service.getRoomById(room.getId()), "original room must still exist");
    }

    @Test
    void createRoomDuplicateNameDifferentObjectThrows() throws RoomServiceException {
        // Same name but different object (different players/status) — the real duplicate-name bug
        Room original = buildRoom("Alpha");
        service.createRoom(original);
        Room other = buildRoom("Alpha");
        other.addPlayer("some-player");
        assertThrows(RoomServiceException.class, () -> service.createRoom(other));
        assertNotNull(service.getRoomById(original.getId()), "original room must still exist");
    }

    @Test
    void createRoomDuplicateNameCaseInsensitiveThrows() throws RoomServiceException {
        Room original = buildRoom("Alpha");
        service.createRoom(original);
        assertThrows(RoomServiceException.class, () -> service.createRoom(buildRoom("alpha")));
        assertThrows(RoomServiceException.class, () -> service.createRoom(buildRoom("ALPHA")));
        assertNotNull(service.getRoomById(original.getId()), "original room must still exist");
    }

    @Test
    void createRoomSetsMinPlayersFromConfig() throws RoomServiceException {
        GameDefinition game = mock(GameDefinition.class);
        when(game.getMinPlayers()).thenReturn(4);
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(game));

        Room room = buildRoom("Alpha");
        service.createRoom(room);

        assertEquals(4, room.getMinPlayers());
    }

    @Test
    void createRoomSetsMaxPlayersFromConfig() throws RoomServiceException {
        GameDefinition game = mock(GameDefinition.class);
        when(game.getMaxPlayers()).thenReturn(6);
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(game));

        Room room = buildRoom("Alpha");
        service.createRoom(room);

        assertEquals(6, room.getMaxPlayers());
    }

    @Test
    void createRoomLeavesMinPlayersAtDefaultWhenGameNotFound() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        assertEquals(1, room.getMinPlayers()); // Room default
    }

    @Test
    void createRoomLeavesMaxPlayersAtDefaultWhenGameNotFound() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        assertEquals(8, room.getMaxPlayers()); // Room default
    }

    @Test
    void createRoomRemovesCreatorFromPreviousRoom() throws RoomServiceException {
        Room roomA = buildRoom("Alpha");
        service.createRoom(roomA);
        service.publishRoom(roomA.getId());
        service.addPlayerIdToRoom("creator-1", roomA.getId());

        Room roomB = buildRoom("Beta"); // same createdByUserId ("creator-1")
        service.createRoom(roomB);
        service.publishRoom(roomB.getId());

        assertEquals(0, service.getRoomById(roomA.getId()).getNrOfPlayers());
        assertEquals(2, service.getRooms().size());
    }

    // ── getRoomById ──────────────────────────────────────────────────────────

    @Test
    void getRoomByIdFindsExistingRoom() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);

        Room found = service.getRoomById(room.getId());
        assertEquals(room, found);
    }

    @Test
    void getRoomByIdReturnsNullForUnknownId() throws RoomServiceException {
        assertNull(service.getRoomById("does-not-exist"));
    }

    // ── deleteInactiveRooms ──────────────────────────────────────────────────
    // A room is "inactive" when it has no live SSE connection. Presence is the
    // signal, not the player list — a room with phantom players (everyone closed
    // their tab) but no subscribers must still be cleaned up.

    @Test
    void roomWithNoSubscribersIsDeletedAfterTtlExpires() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        when(sseRegistry.hasSubscribers(room.getId())).thenReturn(false);

        // Backdate the inactivity timestamp to simulate 15+ minutes with no connection
        roomStore.inactiveSince.put(room.getId(), System.currentTimeMillis() - (16 * 60 * 1000L));

        service.deleteInactiveRooms();

        assertNull(service.getRoomById(room.getId()));
    }

    @Test
    void roomWithNoSubscribersIsKeptIfTtlHasNotExpired() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        when(sseRegistry.hasSubscribers(room.getId())).thenReturn(false);

        service.deleteInactiveRooms(); // first sweep just starts the timer — TTL not yet exceeded

        assertNotNull(service.getRoomById(room.getId()));
        assertTrue(roomStore.inactiveSince.containsKey(room.getId()));
    }

    @Test
    void roomWithLiveSubscriberIsNotScheduledForDeletion() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        when(sseRegistry.hasSubscribers(room.getId())).thenReturn(true);

        // Even with a stale timestamp, a live connection keeps the room alive
        roomStore.inactiveSince.put(room.getId(), System.currentTimeMillis() - (16 * 60 * 1000L));
        service.deleteInactiveRooms();

        assertNotNull(service.getRoomById(room.getId()));
        assertFalse(roomStore.inactiveSince.containsKey(room.getId()));
    }

    @Test
    void reconnectingSubscriberCancelsInactivityTimer() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        when(sseRegistry.hasSubscribers(room.getId())).thenReturn(false);
        service.deleteInactiveRooms();                 // no subscribers → timer starts
        assertTrue(roomStore.inactiveSince.containsKey(room.getId()));

        when(sseRegistry.hasSubscribers(room.getId())).thenReturn(true);
        service.deleteInactiveRooms();                 // a connection appears → timer cancelled

        assertFalse(roomStore.inactiveSince.containsKey(room.getId()));
    }

    @Test
    void playingRoomWithNoSubscribersIsNotDeleted() throws RoomServiceException {
        // During play the client navigates away from the GameRoom page, so a
        // PLAYING room legitimately has no SSE subscribers. It must be left to
        // verifyGameSessionsExist, never deleted by the inactivity sweep.
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        room.setStatus(GameStatus.PLAYING);

        roomStore.inactiveSince.put(room.getId(), System.currentTimeMillis() - (16 * 60 * 1000L));
        service.deleteInactiveRooms();

        assertNotNull(service.getRoomById(room.getId()));
        verify(sseRegistry, never()).hasSubscribers(room.getId());
    }

    @Test
    void testRoomIsNeverDeletedByInactivitySweep() throws RoomServiceException {
        Room room = buildRoom("Test Room");
        service.createRoom(room);
        service.publishRoom(room.getId());

        roomStore.inactiveSince.put(room.getId(), System.currentTimeMillis() - (16 * 60 * 1000L));
        service.deleteInactiveRooms();

        assertNotNull(service.getRoomById(room.getId()));
    }

    // ── deleteRoom ───────────────────────────────────────────────────────────
    // deleteRoom() is the GWT-RPC path (not Spring Security protected).
    // Authorization is enforced by checking the playerid cookie against the room creator.

    @Test
    void deleteRoomByCreatorRemovesIt() throws RoomServiceException {
        // Default spy returns "creator-1" which matches buildRoom()'s createdByUserId.
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.deleteRoom(room.getId());

        assertNull(service.getRoomById(room.getId()));
    }

    @Test
    void deleteRoomByNonCreatorIsRejected() throws RoomServiceException {
        doReturn("stranger").when(service).getPlayerIdFromRequest();
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.deleteRoom(room.getId());

        assertNotNull(service.getRoomById(room.getId()), "non-creator must not be able to delete the room");
    }

    @Test
    void deleteRoomWithNoCallerCookieIsRejected() throws RoomServiceException {
        doReturn("").when(service).getPlayerIdFromRequest();
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.deleteRoom(room.getId());

        assertNotNull(service.getRoomById(room.getId()), "unauthenticated caller must not be able to delete the room");
    }

    @Test
    void deleteNonExistentRoomIsNoOp() throws RoomServiceException {
        service.deleteRoom("non-existent-id"); // must not throw
        assertTrue(service.getRooms().isEmpty());
    }

    // ── updateRoom ───────────────────────────────────────────────────────────

    @Test
    void updateRoomReplacesExisting() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        room.setUniqueProfilePics(true);
        service.updateRoom(room);

        assertEquals(1, service.getRooms().size());
        assertTrue(service.getRooms().get(0).isUniqueProfilePics());
    }

    // ── addPlayerIdToRoom ────────────────────────────────────────────────────

    @Test
    void addPlayerIdToRoomAppendsId() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());

        assertEquals(1, service.getRoomById(room.getId()).getNrOfPlayers());
    }

    @Test
    void addPlayerIdToRoomSetsStatusToFullWhenAtCapacity() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setMaxPlayers(2);
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());

        assertEquals(GameStatus.WAITING, service.getRoomById(room.getId()).getStatus());

        service.addPlayerIdToRoom("player-2", room.getId());

        assertEquals(GameStatus.FULL, service.getRoomById(room.getId()).getStatus());
    }

    @Test
    void addPlayerIdToRoomStatusRemainsWaitingBelowCapacity() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setMaxPlayers(4);
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-2", room.getId());
        service.addPlayerIdToRoom("player-3", room.getId());

        assertEquals(GameStatus.WAITING, service.getRoomById(room.getId()).getStatus());
    }

    @Test
    void addPlayerIdToRoomMovesPlayerFromOldRoomToNewRoom() throws RoomServiceException {
        Room roomA = buildRoom("Alpha");
        Room roomB = buildRoom("Beta");
        service.createRoom(roomA);
        service.createRoom(roomB);
        service.addPlayerIdToRoom("player-1", roomA.getId());

        service.addPlayerIdToRoom("player-1", roomB.getId());

        assertEquals(0, service.getRoomById(roomA.getId()).getNrOfPlayers(), "player must leave old room");
        assertEquals(1, service.getRoomById(roomB.getId()).getNrOfPlayers(), "player must be in new room");
    }

    @Test
    void playerStuckInOldRoomIsMovedToNewRoomOnJoin() throws RoomServiceException {
        // Reproduces bug: browser crash left player listed in roomA; a new room (roomB) was
        // created and a game started there. On re-login the player should see rejoin for roomB,
        // not roomA. addPlayerIdToRoom must move them automatically.
        Room roomA = buildRoom("Alpha");
        Room roomB = buildRoom("Beta");
        service.createRoom(roomA);
        service.createRoom(roomB);
        service.publishRoom(roomB.getId());
        service.addPlayerIdToRoom("player-1", roomA.getId()); // stuck in old room due to crash

        service.addPlayerIdToRoom("player-1", roomB.getId()); // joining the new room

        assertFalse(service.getRoomById(roomA.getId()).getPlayerIds().contains("player-1"),
                "player must no longer appear in old room");
        assertTrue(service.getRoomById(roomB.getId()).getPlayerIds().contains("player-1"),
                "player must appear in new room so the rejoin button shows correctly");
    }

    @Test
    void addingPlayerToOldRoomDoesNotAffectOtherPlayersInIt() throws RoomServiceException {
        Room roomA = buildRoom("Alpha");
        Room roomB = buildRoom("Beta");
        service.createRoom(roomA);
        service.createRoom(roomB);
        service.addPlayerIdToRoom("player-1", roomA.getId());
        service.addPlayerIdToRoom("player-2", roomA.getId()); // other player stays in roomA

        service.addPlayerIdToRoom("player-1", roomB.getId()); // player-1 moves to roomB

        assertTrue(service.getRoomById(roomA.getId()).getPlayerIds().contains("player-2"),
                "other players in the old room must be unaffected");
        assertEquals(1, service.getRoomById(roomA.getId()).getNrOfPlayers());
    }

    @Test
    void addPlayerIdToRoomAllowsRejoiningSameRoom() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-1", room.getId()); // rejoin = idempotent

        assertEquals(1, service.getRoomById(room.getId()).getNrOfPlayers());
    }

    @Test
    void addPlayerIdToRoomIgnoresDuplicates() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());

        assertEquals(1, service.getRoomById(room.getId()).getNrOfPlayers());
    }

    // ── removePlayerFromRoom ─────────────────────────────────────────────────

    @Test
    void removePlayerFromRoomRemovesPlayer() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.setUsernameAndProfile(room, "player-1", "Alice", "3");

        service.removePlayerFromRoom("player-1", room.getId());

        Room found = service.getRoomById(room.getId());
        assertEquals(0, found.getNrOfPlayers());
        assertFalse(found.getPlayerNames().containsKey("player-1"));
    }

    @Test
    void removeCreatorTransfersOwnershipToNextPlayer() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setCreatedByUserId("player-1");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-2", room.getId());

        service.removePlayerFromRoom("player-1", room.getId());

        assertEquals("player-2", service.getRoomById(room.getId()).getCreatedByUserId());
    }

    @Test
    void removePlayerFromFullRoomRevertsStatusToWaiting() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setMaxPlayers(2);
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-2", room.getId());
        assertEquals(GameStatus.FULL, service.getRoomById(room.getId()).getStatus());

        service.removePlayerFromRoom("player-1", room.getId());

        assertEquals(GameStatus.WAITING, service.getRoomById(room.getId()).getStatus());
    }

    @Test
    void removePlayerFromWaitingRoomKeepsStatusWaiting() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setMaxPlayers(4);
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-2", room.getId());

        service.removePlayerFromRoom("player-1", room.getId());

        assertEquals(GameStatus.WAITING, service.getRoomById(room.getId()).getStatus());
    }

    @Test
    void removePlayerFromPlayingRoomKeepsStatusPlaying() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setMaxPlayers(2);
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.addPlayerIdToRoom("player-2", room.getId());
        room.setStatus(GameStatus.PLAYING); // game started

        service.removePlayerFromRoom("player-1", room.getId());

        assertEquals(GameStatus.PLAYING, service.getRoomById(room.getId()).getStatus());
    }

    @Test
    void playerCanJoinNewRoomAfterLeavingPreviousOne() throws RoomServiceException {
        Room roomA = buildRoom("Alpha");
        Room roomB = buildRoom("Beta");
        service.createRoom(roomA);
        service.createRoom(roomB);
        service.addPlayerIdToRoom("player-1", roomA.getId());

        service.removePlayerFromRoom("player-1", roomA.getId());
        service.addPlayerIdToRoom("player-1", roomB.getId());

        assertEquals(0, service.getRoomById(roomA.getId()).getNrOfPlayers());
        assertEquals(1, service.getRoomById(roomB.getId()).getNrOfPlayers());
    }

    @Test
    void removeLastPlayerLeavesNoCreator() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setCreatedByUserId("player-1");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());

        service.removePlayerFromRoom("player-1", room.getId());

        // playerIds is empty — no ownership transfer attempted
        assertEquals(0, service.getRoomById(room.getId()).getNrOfPlayers());
    }

    // ── setUsernameAndProfile ────────────────────────────────────────────────

    @Test
    void setUsernameAndProfileStoresBoth() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.setUsernameAndProfile(room, "player-1", "Alice", "5");

        Room found = service.getRoomById(room.getId());
        assertEquals("Alice", found.getPlayerNames().get("player-1"));
        assertEquals("5", found.getPlayerProfiles().get("player-1"));
    }

    @Test
    void setUsernameAndProfileOverwritesPrevious() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        service.setUsernameAndProfile(room, "player-1", "Alice", "5");
        service.setUsernameAndProfile(room, "player-1", "Alice2", "7");

        assertEquals("Alice2", service.getRoomById(room.getId()).getPlayerNames().get("player-1"));
        assertEquals("7",      service.getRoomById(room.getId()).getPlayerProfiles().get("player-1"));
    }

    // ── getAvailableGames ────────────────────────────────────────────────────

    @Test
    void getAvailableGamesReturnsOnlyReachableGames() throws RoomServiceException {
        // No games configured in mock → should return empty list (not throw)
        when(gamesConfig.getAvailable()).thenReturn(new ArrayList<>());
        assertTrue(service.getAvailableGames().isEmpty());
    }

    // ── verifyGameSessionsExist ──────────────────────────────────────────────

    private Room buildPlayingRoom(String name, String sessionId) {
        Room r = buildRoom(name);
        r.setStatus(GameStatus.PLAYING);
        r.setGameSessionId(sessionId);
        roomStore.rooms.add(r);
        return r;
    }

    private GameDefinition gameDefWithBaseUrl(String baseUrl) {
        GameDefinition def = new GameDefinition();
        def.setId("keezen");
        def.setBaseUrl(baseUrl);
        return def;
    }

    @Test
    void verifyGameSessionsExistDeletesRoomWhenSessionGone() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenThrow(new ResourceAccessException("connection refused"));

        service.verifyGameSessionsExist();

        assertNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistKeepsRoomWhenVersionChanges() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenReturn(Map.of());
        when(restTemplate.getForObject("http://game-server/gamestates/session-1", Map.class))
                .thenReturn(Map.of("version", 1))
                .thenReturn(Map.of("version", 2));

        service.verifyGameSessionsExist(); // stores version "1"
        roomStore.gameStateVersionTimestamps.put(room.getId(), System.currentTimeMillis() - (2 * 60 * 60 * 1000L));
        service.verifyGameSessionsExist(); // version changed to "2" → timer resets, no deletion

        assertNotNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistDeletesRoomWhenVersionStaleForOneHour() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenReturn(Map.of());
        when(restTemplate.getForObject("http://game-server/gamestates/session-1", Map.class))
                .thenReturn(Map.of("version", 42));

        service.verifyGameSessionsExist(); // stores version "42" with current timestamp
        // Backdate the timestamp to simulate 1+ hour of no change
        roomStore.gameStateVersionTimestamps.put(room.getId(), System.currentTimeMillis() - (61 * 60 * 1000L));
        service.verifyGameSessionsExist(); // same version, stale → should delete

        verify(restTemplate).delete("http://game-server/games/session-1");
        assertNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistKeepsRoomWhenVersionUnchangedLessThanOneHour() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenReturn(Map.of());
        when(restTemplate.getForObject("http://game-server/gamestates/session-1", Map.class))
                .thenReturn(Map.of("version", 42));

        service.verifyGameSessionsExist(); // stores version "42"
        service.verifyGameSessionsExist(); // same version, but < 1 hour → keep

        verify(restTemplate, never()).delete(anyString());
        assertNotNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistSkipsTestRoom() {
        buildPlayingRoom("Test Room", "session-test");

        service.verifyGameSessionsExist();

        verify(restTemplate, never()).getForObject(anyString(), any());
    }

    @Test
    void verifyGameSessionsExistSkipsRoomWithNoGameDefinition() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.empty());

        service.verifyGameSessionsExist();

        verify(restTemplate, never()).getForObject(anyString(), any());
        assertNotNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistSkipsVersionCheckWhenVersionMissing() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenReturn(Map.of());
        when(restTemplate.getForObject("http://game-server/gamestates/session-1", Map.class))
                .thenReturn(Map.of()); // no version field

        service.verifyGameSessionsExist();
        roomStore.gameStateVersionTimestamps.put(room.getId(), System.currentTimeMillis() - (2 * 60 * 60 * 1000L));
        service.verifyGameSessionsExist();

        verify(restTemplate, never()).delete(anyString());
        assertNotNull(service.getRoomById(room.getId()));
    }

    @Test
    void verifyGameSessionsExistResetsRoomToWaitingWhenGameFinished() {
        Room room = buildPlayingRoom("Alpha", "session-1");
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDefWithBaseUrl("http://game-server")));
        // GET /games/{sid} returns status: FINISHED — matches Qwixx's GameInfo OpenAPI shape
        when(restTemplate.getForObject("http://game-server/games/session-1", Map.class))
                .thenReturn(Map.of("sessionId", "session-1", "status", "FINISHED",
                                   "playerCount", 1, "maxPlayers", 4));

        service.verifyGameSessionsExist();

        Room updated = service.getRoomById(room.getId());
        assertNotNull(updated, "room must still exist after game finishes");
        assertEquals(GameStatus.WAITING, updated.getStatus(), "room must reset to WAITING");
        assertNull(updated.getGameSessionId(), "session ID must be cleared");
    }

    // ── SSE emit verification ────────────────────────────────────────────────

    @Test
    void publishRoomEmitsLobbyAndRoomSseUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        reset(lobbySseRegistry, sseRegistry);

        service.publishRoom(room.getId());

        verify(lobbySseRegistry).emit(argThat(list -> list.size() == 1));
        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    @Test
    void addPlayerIdToRoomEmitsLobbyAndRoomSseUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(lobbySseRegistry, sseRegistry);

        service.addPlayerIdToRoom("player-1", room.getId());

        verify(lobbySseRegistry).emit(any());
        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    @Test
    void removePlayerFromRoomEmitsLobbyAndRoomSseUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        reset(lobbySseRegistry, sseRegistry);

        service.removePlayerFromRoom("player-1", room.getId());

        verify(lobbySseRegistry).emit(any());
        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    @Test
    void setUsernameAndProfileEmitsRoomSseUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        reset(sseRegistry);

        service.setUsernameAndProfile(room, "player-1", "Alice", "3");

        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    @Test
    void updateRoomEmitsLobbyAndRoomSseUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(lobbySseRegistry, sseRegistry);

        room.setUniqueProfilePics(false);
        service.updateRoom(room);

        verify(lobbySseRegistry).emit(any());
        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    @Test
    void deleteRoomByCreatorEmitsSseClosedAndLobbyUpdate() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(lobbySseRegistry, sseRegistry);

        service.deleteRoom(room.getId());

        verify(sseRegistry).emitClosed(room.getId());
        verify(lobbySseRegistry).emit(argThat(List::isEmpty));
    }

    // ── updateRoom (in-place field update) ───────────────────────────────────

    @Test
    void updateRoomPreservesPlayerIds() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());

        // Send an update that carries no player list (as a real client copy would)
        Room update = new Room();
        update.setId(room.getId());
        update.setGameId("qwixx");
        update.setMaxPlayers(4);
        service.updateRoom(update);

        Room stored = service.getRoomById(room.getId());
        assertTrue(stored.getPlayerIds().contains("player-1"),
                "updateRoom must not wipe existing player IDs");
    }

    @Test
    void updateRoomPreservesPlayerNames() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.addPlayerIdToRoom("player-1", room.getId());
        service.setUsernameAndProfile(room, "player-1", "Alice", "0");

        Room update = new Room();
        update.setId(room.getId());
        update.setGameId("qwixx");
        service.updateRoom(update);

        Room stored = service.getRoomById(room.getId());
        assertEquals("Alice", stored.getPlayerNames().get("player-1"),
                "updateRoom must not wipe existing player names");
    }

    @Test
    void updateRoomPreservesStatus() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        // Add players to trigger FULL status
        room.setMaxPlayers(1);
        service.updateRoom(room);
        service.addPlayerIdToRoom("player-1", room.getId());
        assertEquals(GameStatus.FULL, service.getRoomById(room.getId()).getStatus());

        // updateRoom must not reset the status
        Room update = new Room();
        update.setId(room.getId());
        update.setMaxPlayers(1);
        service.updateRoom(update);

        assertEquals(GameStatus.FULL, service.getRoomById(room.getId()).getStatus(),
                "updateRoom must not reset room status");
    }

    @Test
    void updateRoomUpdatesGameOptionsAndGameId() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        HashMap<String, String> opts = new HashMap<>();
        opts.put("extraRow", "true");
        Room update = new Room();
        update.setId(room.getId());
        update.setGameId("qwixx");
        update.setGameOptions(opts);
        service.updateRoom(update);

        Room stored = service.getRoomById(room.getId());
        assertEquals("qwixx", stored.getGameId());
        assertEquals("true", stored.getGameOptions().get("extraRow"));
    }

    // ── setRoomGame ──────────────────────────────────────────────────────────

    @Test
    void setRoomGameByCreatorSetsGameId() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.setRoomGame(room.getId(), "qwixx");

        assertEquals("qwixx", service.getRoomById(room.getId()).getGameId());
    }

    @Test
    void setRoomGameWipesExistingOptions() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        // seed options first
        Room update = new Room();
        update.setId(room.getId());
        HashMap<String, String> opts = new HashMap<>();
        opts.put("extraRow", "true");
        update.setGameOptions(opts);
        service.updateRoom(update);

        service.setRoomGame(room.getId(), "qwixx");

        assertTrue(service.getRoomById(room.getId()).getGameOptions().isEmpty(),
                "game options must be wiped when the game changes");
    }

    @Test
    void setRoomGameUpdatesMinMaxFromConfig() throws RoomServiceException {
        GameDefinition def = new GameDefinition();
        def.setId("qwixx");
        def.setMinPlayers(2);
        def.setMaxPlayers(5);
        def.setBaseUrl("http://qwixx");
        when(gamesConfig.findById("qwixx")).thenReturn(Optional.of(def));

        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.setRoomGame(room.getId(), "qwixx");

        Room stored = service.getRoomById(room.getId());
        assertEquals(2, stored.getMinPlayers());
        assertEquals(5, stored.getMaxPlayers());
        assertEquals("http://qwixx", stored.getGameBaseUrl());
    }

    @Test
    void setRoomGameByNonCreatorWithPermissionSucceeds() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setAnyPlayerCanSelectGame(true);
        service.createRoom(room);
        service.publishRoom(room.getId());
        doReturn("other-player").when(service).getPlayerIdFromRequest();

        assertDoesNotThrow(() -> service.setRoomGame(room.getId(), "qwixx"));
        assertEquals("qwixx", service.getRoomById(room.getId()).getGameId());
    }

    @Test
    void setRoomGameByNonCreatorWithoutPermissionThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        doReturn("other-player").when(service).getPlayerIdFromRequest();

        assertThrows(RoomServiceException.class,
                () -> service.setRoomGame(room.getId(), "qwixx"),
                "non-creator without permission must not be able to change the game");
    }

    @Test
    void setRoomGameUnknownRoomThrows() {
        assertThrows(RoomServiceException.class,
                () -> service.setRoomGame("no-such-room", "qwixx"));
    }

    @Test
    void setRoomGameEmitsRoomAndLobbySse() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(lobbySseRegistry, sseRegistry);

        service.setRoomGame(room.getId(), "qwixx");

        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
        verify(lobbySseRegistry).emit(any());
    }

    // ── setRoomPermissions ───────────────────────────────────────────────────

    @Test
    void setRoomPermissionsByCreatorUpdatesFlags() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.setRoomPermissions(room.getId(), true, true);

        Room stored = service.getRoomById(room.getId());
        assertTrue(stored.isAnyPlayerCanSelectGame());
        assertTrue(stored.isAnyPlayerCanSetOptions());
    }

    @Test
    void setRoomPermissionsCanBeClearedByCreator() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setAnyPlayerCanSelectGame(true);
        room.setAnyPlayerCanSetOptions(true);
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.setRoomPermissions(room.getId(), false, false);

        Room stored = service.getRoomById(room.getId());
        assertFalse(stored.isAnyPlayerCanSelectGame());
        assertFalse(stored.isAnyPlayerCanSetOptions());
    }

    @Test
    void setRoomPermissionsByNonCreatorThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        doReturn("other-player").when(service).getPlayerIdFromRequest();

        assertThrows(RoomServiceException.class,
                () -> service.setRoomPermissions(room.getId(), true, false));
    }

    @Test
    void setRoomPermissionsUnknownRoomThrows() {
        assertThrows(RoomServiceException.class,
                () -> service.setRoomPermissions("no-such-room", true, false));
    }

    @Test
    void setRoomPermissionsEmitsRoomSse() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(sseRegistry);

        service.setRoomPermissions(room.getId(), true, false);

        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
    }

    // ─── setRoomPassword ─────────────────────────────────────────────────────

    @Test
    void setRoomPasswordByCreatorGeneratesPassword() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());

        service.setRoomPassword(room.getId(), true);

        Room stored = service.getRoomById(room.getId());
        assertTrue(stored.hasPassword(), "room should have a password after enabling");
        assertNotNull(stored.getRoomPassword());
        assertEquals(5, stored.getRoomPassword().length(), "password must be 5 characters");
    }

    @Test
    void generatedPasswordMatchesCvcvcPattern() throws RoomServiceException {
        // Run several times to reduce the chance of a false-negative
        String vowels     = "AEU";
        String consonants = "CDFHJKMNPRTWXY";
        for (int i = 0; i < 50; i++) {
            Room room = buildRoom("Alpha" + i);
            service.createRoom(room);
            service.publishRoom(room.getId());
            service.setRoomPassword(room.getId(), true);
            String pwd = service.getRoomById(room.getId()).getRoomPassword();
            assertTrue(consonants.indexOf(pwd.charAt(0)) >= 0, "char 0 must be a consonant: " + pwd);
            assertTrue(vowels    .indexOf(pwd.charAt(1)) >= 0, "char 1 must be a vowel: "     + pwd);
            assertTrue(consonants.indexOf(pwd.charAt(2)) >= 0, "char 2 must be a consonant: " + pwd);
            assertTrue(vowels    .indexOf(pwd.charAt(3)) >= 0, "char 3 must be a vowel: "     + pwd);
            assertTrue(consonants.indexOf(pwd.charAt(4)) >= 0, "char 4 must be a consonant: " + pwd);
        }
    }

    @Test
    void setRoomPasswordByCreatorClearsPassword() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        service.setRoomPassword(room.getId(), true);

        service.setRoomPassword(room.getId(), false);

        assertFalse(service.getRoomById(room.getId()).hasPassword(),
                "room should have no password after disabling");
    }

    @Test
    void setRoomPasswordByNonCreatorThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        doReturn("other-player").when(service).getPlayerIdFromRequest();

        assertThrows(RoomServiceException.class,
                () -> service.setRoomPassword(room.getId(), true),
                "non-creator must not be able to set a room password");
    }

    @Test
    void setRoomPasswordUnknownRoomThrows() {
        assertThrows(RoomServiceException.class,
                () -> service.setRoomPassword("no-such-room", true));
    }

    @Test
    void setRoomPasswordEmitsRoomAndLobbySse() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        service.createRoom(room);
        service.publishRoom(room.getId());
        reset(sseRegistry);
        reset(lobbySseRegistry);

        service.setRoomPassword(room.getId(), true);

        verify(sseRegistry).emit(eq(room.getId()), any(Room.class));
        verify(lobbySseRegistry).emit(any());
    }

    // ── startGame guard: no game selected ────────────────────────────────────

    @Test
    void startGameWithNoGameSelectedThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setGameId(null);
        service.createRoom(room);
        service.publishRoom(room.getId());

        RoomServiceException ex = assertThrows(RoomServiceException.class,
                () -> service.startGame(room.getId()));
        assertTrue(ex.getMessage().contains("No game selected"),
                "Expected 'No game selected' in message but got: " + ex.getMessage());
    }

    @Test
    void startGameWithEmptyGameIdThrows() throws RoomServiceException {
        Room room = buildRoom("Alpha");
        room.setGameId("");
        service.createRoom(room);
        service.publishRoom(room.getId());

        RoomServiceException ex = assertThrows(RoomServiceException.class,
                () -> service.startGame(room.getId()));
        assertTrue(ex.getMessage().contains("No game selected"),
                "Expected 'No game selected' in message but got: " + ex.getMessage());
    }

    // ── startGame: default options when none configured ──────────────────────

    /**
     * When a player selects a game but never opens the GameOptions dialog, the
     * room's gameOptions map stays empty.  startGame must NOT simply omit the
     * gameOptions field — that causes the game server to start with an empty /
     * disabled configuration.  Instead it should fetch the game's declared
     * defaults via GET /game-options and forward those as the initial options.
     *
     * <p>This test describes the <em>desired</em> behaviour and will therefore
     * FAIL until the fix is applied (confirming the bug is present).
     * The lenient stub for GET /game-options is intentional: before the fix the
     * production code never calls that endpoint inside startGame, so a non-lenient
     * stub would cause a spurious "unused stubbing" error that would hide the real
     * failing assertion.
     */
    @Test
    void startGameWithEmptyOptionsUsesGameServerDefaults() throws RoomServiceException {
        // Room with a game selected but options never configured (dialog never opened)
        Room room = buildRoom("Alpha");
        room.setGameOptions(new HashMap<>());
        room.getPlayerNames().put("p1", "Alice");
        room.getPlayerNames().put("p2", "Bob");
        service.createRoom(room);
        service.publishRoom(room.getId());

        GameDefinition gameDef = gameDefWithBaseUrl("http://game-server");
        gameDef.setMinPlayers(2);
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDef));

        // Default option declared by the game server: base = true
        GameOption baseOption = new GameOption();
        baseOption.setKey("base");
        baseOption.setType("BOOLEAN");
        baseOption.setDefaultValue("true");
        // lenient: the current (buggy) code does not call GET /game-options inside
        // startGame, so without lenient() Mockito's strict-stub check would fire an
        // "unnecessary stubbing" failure that obscures the real assertion failure.
        lenient().when(restTemplate.getForObject("http://game-server/game-options", GameOption[].class))
                .thenReturn(new GameOption[]{baseOption});

        when(restTemplate.postForObject(eq("http://game-server/games"), any(), eq(Map.class)))
                .thenReturn(Map.of("sessionId", "test-session"));

        service.startGame(room.getId());

        // Inspect what was forwarded to POST /games (create-session request)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(eq("http://game-server/games"), captor.capture(), eq(Map.class));
        Map<String, Object> sentRequest = captor.getValue();

        // The game should be started with the defaults, not an absent/empty options block
        assertNotNull(sentRequest.get("gameOptions"),
                "gameOptions must be present in the create-session request even when the "
                + "room's options map was never populated by the user");
        @SuppressWarnings("unchecked")
        Map<String, Object> sentOptions = (Map<String, Object>) sentRequest.get("gameOptions");
        assertEquals(true, sentOptions.get("base"),
                "the 'base' default (true) must be forwarded to the game server");
    }
}
