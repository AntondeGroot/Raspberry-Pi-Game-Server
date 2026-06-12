package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.Lobby.RoomService;
import ADG.Lobby.RoomServiceException;
import ADG.config.GamesConfig;
import ADG.config.SpriteSheetsConfig;
import com.google.gwt.user.server.rpc.jakarta.RemoteServiceServlet;
import jakarta.servlet.annotation.WebServlet;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@WebServlet("/app/gameroom")
public class RoomServiceImpl extends RemoteServiceServlet implements RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomServiceImpl.class);

    // Password generation: CVCVC using letters that are unambiguous when read aloud or written.
    // Excluded vowels  : I (→1/L), O (→0)
    // Excluded consonants: B (→8), G (→6/9), L (→1/I), Q (rare/O-like), S (→5), V (→U), Z (→2)
    private static final char[] PWD_VOWELS     = "AEU".toCharArray();
    private static final char[] PWD_CONSONANTS = "CDFHJKMNPRTWXY".toCharArray();
    private static final Random  PWD_RANDOM    = new Random();

    @Autowired
    private GamesConfig gamesConfig;

    @Autowired
    private SpriteSheetsConfig spriteSheetsConfig;

    @Autowired
    private RoomStore roomStore;

    @Value("${server.port:4100}")
    private int serverPort;

    @Autowired
    private RoomSseRegistry sseRegistry;

    @Autowired
    private LobbySseRegistry lobbySseRegistry;

    private static final long EMPTY_ROOM_TTL_MS = 15 * 60 * 1000L;
    private static final long INACTIVE_GAME_TTL_MS = 60 * 60 * 1000L;

    RestTemplate restTemplate = new RestTemplate();

    @Override
    public synchronized ArrayList<Room> getRooms() {
        ArrayList<Room> visible = new ArrayList<>();
        for (Room r : roomStore.rooms) {
            if (r.getStatus() != GameStatus.PENDING) {
                visible.add(r);
            }
        }
        return visible;
    }

    @Override
    public synchronized Room getRoomById(String roomId){
        Optional<Room> result = roomStore.rooms.stream().filter(room -> room.getId().equals(roomId)).findFirst();
        if(result.isPresent()) {
            if(result.get().getId() == null){
                throw new IllegalArgumentException();
            }
            return result.get();
        }
        return null;
    }

    @Override
    public synchronized Room createRoom(Room room) throws RoomServiceException {
        if (room.getName().isBlank() || room.getName().trim().length() < 3) {
            throw new RoomServiceException("Room name must be at least 3 characters.");
        }
        boolean nameAlreadyExists = roomStore.rooms.stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(room.getName().trim()));
        if (nameAlreadyExists) {
            throw new RoomServiceException("A room with this name already exists.");
        }
        roomStore.rooms.stream()
                .filter(r -> r.getPlayerIds().contains(room.getCreatedByUserId())
                          && r.getStatus() != GameStatus.PLAYING)
                .findFirst()
                .ifPresent(r -> removePlayerFromRoom(room.getCreatedByUserId(), r.getId()));

        gamesConfig.findById(room.getGameId()).ifPresent(game -> {
            room.setMinPlayers(game.getMinPlayers());
            room.setMaxPlayers(game.getMaxPlayers());
            room.setGameBaseUrl(game.getBaseUrl());
        });
        room.setStatus(GameStatus.PENDING);
        roomStore.rooms.add(room);
        return room;
    }

    private void emitLobbyUpdate() {
        List<Room> visible = roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .collect(Collectors.toList());
        lobbySseRegistry.emit(visible);
    }

    private void emitRoomUpdate(String roomId) {
        roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .ifPresent(r -> sseRegistry.emit(roomId, r));
    }

    @Override
    public synchronized void publishRoom(String roomId) {
        roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId) && r.getStatus() == GameStatus.PENDING)
                .findFirst()
                .ifPresent(r -> r.setStatus(GameStatus.WAITING));
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    // NOTE: This GWT-RPC method is NOT protected by Spring Security.
    // Authorization is enforced here manually: only the room creator may delete via this path.
    // Admins delete rooms through the Spring Security-protected DELETE /admin/rooms/{id} endpoint instead.
    @Override
    public synchronized void deleteRoom(String roomId) {
        String callerId = getPlayerIdFromRequest();
        boolean isCreator = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .anyMatch(r -> r.getCreatedByUserId().equals(callerId));
        if (!isCreator) return;
        roomStore.deleteRoom(roomId);
        sseRegistry.emitClosed(roomId);
        emitLobbyUpdate();
    }

    String getPlayerIdFromRequest() {
        jakarta.servlet.http.Cookie[] cookies = getThreadLocalRequest().getCookies();
        if (cookies == null) return "";
        for (jakarta.servlet.http.Cookie c : cookies) {
            if ("playerid".equals(c.getName())) return c.getValue();
        }
        return "";
    }

    @Override
    public synchronized void updateRoom(Room room) throws RoomServiceException {
        if (room == null) {
            logger.error("Attempt to update null room");
            throw new RoomServiceException("Room cannot be null");
        }
        if (room.getId() == null || room.getId().isBlank()) {
            logger.error("Attempt to update room with null or empty ID");
            throw new RoomServiceException("Room ID cannot be null or empty");
        }
        Optional<Room> result = roomStore.rooms.stream().filter(r -> r.getId().equals(room.getId())).findFirst();
        if(result.isPresent()) {
            Room foundRoom = result.get();
            // Update only the client-controlled configuration fields.
            // Player state (playerIds, playerNames, playerProfiles), status, gameSessionId,
            // and createdByUserId are authoritative server-side and must never be overwritten
            // by a stale client copy.
            foundRoom.setGameId(room.getGameId());
            foundRoom.setGameOptions(room.getGameOptions());
            foundRoom.setMaxPlayers(room.getMaxPlayers());
            foundRoom.setUniqueProfilePics(room.isUniqueProfilePics());
            foundRoom.setAnyPlayerCanSelectGame(room.isAnyPlayerCanSelectGame());
            foundRoom.setAnyPlayerCanSetOptions(room.isAnyPlayerCanSetOptions());
            // Only update gameBaseUrl if the incoming value is richer (set by server via setRoomGame;
            // client may carry a stale or empty value)
            if (room.getGameBaseUrl() != null && !room.getGameBaseUrl().isBlank()) {
                foundRoom.setGameBaseUrl(room.getGameBaseUrl());
            }
            logger.debug("Room {} updated successfully", room.getId());
        } else {
            logger.error("Room not found with ID: {}", room.getId());
            throw new RoomServiceException("Room with ID " + room.getId() + " not found");
        }
        emitRoomUpdate(room.getId());
        emitLobbyUpdate();
    }

    @Override
    public synchronized void addPlayerIdToRoom(String playerId, String roomId) {
        roomStore.rooms.stream()
                .filter(r -> !r.getId().equals(roomId) && r.getPlayerIds().contains(playerId)
                          && r.getStatus() != GameStatus.PLAYING)
                .findFirst()
                .ifPresent(r -> removePlayerFromRoom(playerId, r.getId()));

        for (Room room1 : roomStore.rooms) {
            if (room1.getId().equals(roomId)) {
                room1.addPlayer(playerId);
                if (room1.getNrOfPlayers() >= room1.getMaxPlayers()) {
                    room1.setStatus(GameStatus.FULL);
                }
            }
        }
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    @Override
    public synchronized void removePlayerFromRoom(String playerId, String roomId) {
        for (Room room1 : roomStore.rooms) {
            if (room1.getId().equals(roomId)) {
                if (room1.getStatus() == GameStatus.PLAYING) return;
                room1.removePlayer(playerId);
                if (room1.getStatus() == GameStatus.FULL && room1.getNrOfPlayers() < room1.getMaxPlayers()) {
                    room1.setStatus(GameStatus.WAITING);
                }
                if (playerId.equals(room1.getCreatedByUserId()) && !room1.getPlayerIds().isEmpty()) {
                    String newCreator = room1.getPlayerIds().get(0);
                    room1.setCreatedByUserId(newCreator);
                }
            }
        }
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    @Override
    public synchronized void setUsernameAndProfile(Room room, String userId, String username, String profileId) {
        for (Room room1 : roomStore.rooms) {
            if (room1.getId().equals(room.getId())) {
                room1.addPlayerName(userId, username);
                room1.addPlayerProfile(userId, profileId);
            }
        }
        emitRoomUpdate(room.getId());
    }

    @Override
    public synchronized Room startGame(String roomId) throws RoomServiceException {
        logger.debug("Starting game for room: {}", roomId);
        for (Room room1 : roomStore.rooms) {
            if (room1.getId().equals(roomId)) {

                if (room1.getGameId() == null || room1.getGameId().isEmpty()) {
                    throw new RoomServiceException("No game selected. Please select a game before starting.");
                }

                GameDefinition game = gamesConfig.findById(room1.getGameId())
                        .orElseThrow(() -> new RoomServiceException("Unknown game: " + room1.getGameId()));

                String baseUrl = game.getBaseUrl();
                logger.debug("Game ID: {}, Base URL: {}", room1.getGameId(), baseUrl);

                // 0. Enforce minimum players
                if (room1.getPlayerNames().size() < game.getMinPlayers()) {
                    throw new RoomServiceException(
                            "Not enough players. Need at least " + game.getMinPlayers()
                            + ", but only " + room1.getPlayerNames().size() + " have joined.");
                }

                // 1. Create a game session
                // Resolve which options to forward.  When the room's options map is
                // empty (the user never opened the GameOptions dialog), fall back to
                // the game server's own declared defaults so the game is never started
                // with a blank / fully-disabled configuration.
                HashMap<String, String> optionsToSend = room1.getGameOptions();
                if (optionsToSend.isEmpty()) {
                    optionsToSend = fetchDefaultOptions(baseUrl);
                }

                Map<String, Object> newGameRequest = new HashMap<>();
                newGameRequest.put("roomName", room1.getName());
                newGameRequest.put("maxPlayers", room1.getPlayerNames().size());
                if (!optionsToSend.isEmpty()) {
                    newGameRequest.put("gameOptions", parseGameOptions(optionsToSend));
                }
                Map sessionResponse;
                try {
                    String createSessionUrl = baseUrl + "/games";
                    logger.debug("Creating session at: {}", createSessionUrl);
                    sessionResponse = restTemplate.postForObject(createSessionUrl, newGameRequest, Map.class);
                    logger.debug("Session created successfully");
                } catch (RestClientException e) {
                    logger.error("Failed to create session: {}", e.getMessage(), e);
                    throw new RoomServiceException("Could not reach game server at " + baseUrl + ": " + e.getMessage());
                }
                String sessionId = sessionResponse.get("sessionId").toString();
                logger.debug("Session ID: {}", sessionId);

                // 2. Add each player & 3. Start the game — clean up the session on any failure
                try {
                    logger.debug("Adding {} players to session", room1.getPlayerNames().size());
                    for (Map.Entry<String, String> entry : room1.getPlayerNames().entrySet()) {
                        String playerId = entry.getKey();
                        String playerName = entry.getValue();
                        String profilePicStr = room1.getPlayerProfiles().get(playerId);
                        int profilePic = 0;
                        if (profilePicStr != null) {
                            try { profilePic = Integer.parseInt(profilePicStr); } catch (NumberFormatException ignored) {}
                        }
                        Map<String, Object> playerRequest = new HashMap<>();
                        playerRequest.put("id", playerId);
                        playerRequest.put("name", playerName);
                        playerRequest.put("profilePic", profilePic);
                        String playerUrl = baseUrl + "/games/" + sessionId + "/players";
                        logger.debug("Adding player {} at: {}", playerName, playerUrl);
                        restTemplate.postForObject(playerUrl, playerRequest, Map.class);
                    }

                    // 3. Start the game — include shuffled bot profile-pic indices so
                    //    each bot gets a unique robot avatar from the bot-only sheet.
                    String startGameUrl = baseUrl + "/games/" + sessionId;
                    logger.debug("Starting game at: {}", startGameUrl);
                    Map<String, Object> startRequest = new HashMap<>();
                    startRequest.put("botProfilePics", botProfileIndices());
                    startRequest.put("callbackUrl", "http://localhost:" + serverPort + "/rooms/" + roomId + "/game-finished");
                    restTemplate.postForObject(startGameUrl, startRequest, Void.class);
                    logger.info("Game started successfully for room: {}", roomId);
                } catch (RestClientException e) {
                    logger.error("Error during game start: {}", e.getMessage(), e);
                    // Attempt to clean up the orphaned session on the game server
                    try {
                        restTemplate.delete(baseUrl + "/games/" + sessionId);
                    } catch (RestClientException ignored) {}
                    throw new RoomServiceException(extractErrorMessage(e));
                }

                // 4. Store session info and mark room as playing
                room1.setGameSessionId(sessionId);
                room1.setGameBaseUrl("/" + game.getId());
                room1.setStatus(GameStatus.PLAYING);
                emitRoomUpdate(roomId);
                emitLobbyUpdate();
                return room1;
            }
        }
        return null;
    }

    @Override
    public synchronized ArrayList<GameOption> getGameOptions(String gameId) throws RoomServiceException {
        GameDefinition game = gamesConfig.findById(gameId).orElse(null);
        if (game == null) return new ArrayList<>();
        try {
            GameOption[] options = restTemplate.getForObject(game.getBaseUrl() + "/game-options", GameOption[].class);
            if (options == null) return new ArrayList<>();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return java.util.Arrays.stream(options)
                    .filter(o -> !o.isAdminOnly() || isAdmin)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (RestClientException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized void setRoomGame(String roomId, String gameId) throws RoomServiceException {
        String callerId = getPlayerIdFromRequest();
        Room found = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new RoomServiceException("Room not found: " + roomId));
        boolean isCreator = found.getCreatedByUserId().equals(callerId);
        if (!isCreator && !found.isAnyPlayerCanSelectGame()) {
            throw new RoomServiceException("Not authorized to select game");
        }
        found.setGameId(gameId);
        found.setGameOptions(new HashMap<>());
        gamesConfig.findById(gameId).ifPresent(game -> {
            found.setMinPlayers(game.getMinPlayers());
            found.setMaxPlayers(game.getMaxPlayers());
            found.setGameBaseUrl(game.getBaseUrl());
            found.setEmbeddedSettings(game.isEmbeddedSettings());
        });
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    @Override
    public synchronized void setRoomPermissions(String roomId, boolean anyPlayerCanSelectGame, boolean anyPlayerCanSetOptions) throws RoomServiceException {
        String callerId = getPlayerIdFromRequest();
        Room found = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new RoomServiceException("Room not found: " + roomId));
        if (!found.getCreatedByUserId().equals(callerId)) {
            throw new RoomServiceException("Only the room creator can change permissions");
        }
        found.setAnyPlayerCanSelectGame(anyPlayerCanSelectGame);
        found.setAnyPlayerCanSetOptions(anyPlayerCanSetOptions);
        emitRoomUpdate(roomId);
    }

    @Override
    public synchronized void setRoomPassword(String roomId, boolean enabled) throws RoomServiceException {
        String callerId = getPlayerIdFromRequest();
        Room found = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new RoomServiceException("Room not found: " + roomId));
        if (!found.getCreatedByUserId().equals(callerId)) {
            throw new RoomServiceException("Only the room creator can set a room password");
        }
        found.setRoomPassword(enabled ? generateRoomPassword() : null);
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    /** Generates a CVCVC password using unambiguous letters. */
    private String generateRoomPassword() {
        char[] pwd = new char[5];
        pwd[0] = PWD_CONSONANTS[PWD_RANDOM.nextInt(PWD_CONSONANTS.length)];
        pwd[1] = PWD_VOWELS[PWD_RANDOM.nextInt(PWD_VOWELS.length)];
        pwd[2] = PWD_CONSONANTS[PWD_RANDOM.nextInt(PWD_CONSONANTS.length)];
        pwd[3] = PWD_VOWELS[PWD_RANDOM.nextInt(PWD_VOWELS.length)];
        pwd[4] = PWD_CONSONANTS[PWD_RANDOM.nextInt(PWD_CONSONANTS.length)];
        return new String(pwd);
    }

    @Override
    public synchronized ArrayList<GameDefinition> getAvailableGames() {
        ArrayList<GameDefinition> reachable = new ArrayList<>();
        for (GameDefinition game : gamesConfig.getAvailable()) {
            if (isReachable(game.getHealthUrl())) {
                reachable.add(game);
            }
        }
        return reachable;
    }

    /**
     * Deletes rooms that have been abandoned — i.e. have had no live SSE connection
     * for {@link #EMPTY_ROOM_TTL_MS}. A room's SSE stream is the presence signal:
     * when every player closes their tab (or otherwise disconnects) the room would
     * otherwise linger forever with phantom players in its list.
     *
     * PLAYING rooms are deliberately excluded: while a game is running the client
     * navigates away from the GameRoom page to the game server, so a PLAYING room
     * legitimately has zero subscribers. Those are governed by
     * {@link #verifyGameSessionsExist()} (game-server health) instead.
     */
    @Scheduled(fixedDelay = 60_000)
    public synchronized void deleteInactiveRooms() {
        long now = System.currentTimeMillis();
        List<String> deleted = new ArrayList<>();
        for (Room room : roomStore.rooms) {
            boolean exempt = room.getStatus() == GameStatus.PLAYING
                    || "Test Room".equals(room.getName())
                    || sseRegistry.hasSubscribers(room.getId());
            if (exempt) {
                roomStore.inactiveSince.remove(room.getId());
                continue;
            }
            long since = roomStore.inactiveSince.computeIfAbsent(room.getId(), k -> now);
            if (now - since >= EMPTY_ROOM_TTL_MS) {
                deleted.add(room.getId());
            }
        }
        if (!deleted.isEmpty()) {
            deleted.forEach(id -> {
                roomStore.deleteRoom(id);
                sseRegistry.emitClosed(id);
            });
            emitLobbyUpdate();
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public synchronized void verifyGameSessionsExist() {
        ArrayList<Room> playingRooms = new ArrayList<>();
        for (Room room : roomStore.rooms) {
            if (room.getStatus() == GameStatus.PLAYING && room.getGameSessionId() != null) {
                playingRooms.add(room);
            }
        }

        boolean anyDeleted = false;
        for (Room room : playingRooms) {
            if ("Test Room".equals(room.getName())) {
                continue;
            }

            Optional<GameDefinition> gameDef = gamesConfig.findById(room.getGameId());
            if (gameDef.isEmpty()) {
                logger.warn("No game definition found for room {}, skipping health check", room.getId());
                continue;
            }
            String baseUrl = gameDef.get().getBaseUrl();
            String sessionId = room.getGameSessionId();

            Map<?, ?> gameInfo;
            try {
                String gameUrl = baseUrl + "/games/" + sessionId;
                logger.debug("Verifying game session exists at: {}", gameUrl);
                gameInfo = restTemplate.getForObject(gameUrl, Map.class);
                logger.debug("Game session verified: {}", room.getId());
            } catch (RestClientException e) {
                logger.warn("Game session no longer exists for room {}: {}", room.getId(), e.getMessage());
                roomStore.deleteRoom(room.getId());
                sseRegistry.emitClosed(room.getId());
                anyDeleted = true;
                continue;
            }

            if (gameInfo != null && "FINISHED".equals(gameInfo.get("status"))) {
                logger.info("Game session finished for room {}, resetting to WAITING", room.getId());
                room.setStatus(GameStatus.WAITING);
                room.setGameSessionId(null);
                roomStore.gameStateVersions.remove(room.getId());
                roomStore.gameStateVersionTimestamps.remove(room.getId());
                emitRoomUpdate(room.getId());
                emitLobbyUpdate();
                continue;
            }

            Map<?, ?> gameStateData;
            try {
                gameStateData = restTemplate.getForObject(baseUrl + "/gamestates/" + sessionId, Map.class);
            } catch (RestClientException e) {
                logger.warn("Could not fetch game state for room {}: {}", room.getId(), e.getMessage());
                continue;
            }

            if (gameStateData == null) {
                continue;
            }

            Object versionObj = gameStateData.get("version");
            if (versionObj == null) {
                continue;
            }
            String currentVersion = versionObj.toString();
            String storedVersion = roomStore.gameStateVersions.get(room.getId());
            long now = System.currentTimeMillis();

            if (!currentVersion.equals(storedVersion)) {
                roomStore.gameStateVersions.put(room.getId(), currentVersion);
                roomStore.gameStateVersionTimestamps.put(room.getId(), now);
            } else {
                Long firstSeenAt = roomStore.gameStateVersionTimestamps.get(room.getId());
                if (firstSeenAt != null && now - firstSeenAt >= INACTIVE_GAME_TTL_MS) {
                    logger.info("Game state unchanged for 1 hour in room {}, removing stale game", room.getId());
                    try {
                        restTemplate.delete(baseUrl + "/games/" + sessionId);
                    } catch (RestClientException e) {
                        logger.warn("Failed to delete stale game session {}: {}", sessionId, e.getMessage());
                    }
                    roomStore.deleteRoom(room.getId());
                    sseRegistry.emitClosed(room.getId());
                    anyDeleted = true;
                }
            }
        }
        if (anyDeleted) emitLobbyUpdate();
    }

    /**
     * Fetches the game-option definitions from the game server and returns a map
     * of {@code key → defaultValue} for every option that has a non-null default.
     * Returns an empty map if the endpoint is unreachable or returns no options,
     * so the caller can still proceed without options in degraded scenarios.
     */
    private HashMap<String, String> fetchDefaultOptions(String baseUrl) {
        try {
            GameOption[] options = restTemplate.getForObject(baseUrl + "/game-options", GameOption[].class);
            if (options == null) return new HashMap<>();
            HashMap<String, String> defaults = new HashMap<>();
            for (GameOption opt : options) {
                if (opt.getDefaultValue() != null) {
                    defaults.put(opt.getKey(), opt.getDefaultValue());
                }
            }
            return defaults;
        } catch (Exception e) {
            logger.warn("Could not fetch default game options from {}: {}", baseUrl, e.getMessage());
            return new HashMap<>();
        }
    }

    private List<Integer> botProfileIndices() {
        return spriteSheetsConfig.botProfileIndices();
    }

    private Map<String, Object> parseGameOptions(HashMap<String, String> raw) {
        Map<String, Object> parsed = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            parsed.put(entry.getKey(), parseOptionValue(entry.getValue()));
        }
        return parsed;
    }

    private Object parseOptionValue(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    private String extractErrorMessage(RestClientException e) {
        if (e instanceof HttpClientErrorException hce) {
            try {
                Map<?, ?> body = new ObjectMapper().readValue(hce.getResponseBodyAsString(), Map.class);
                Object msg = body.get("message");
                if (msg != null) return msg.toString();
            } catch (Exception ignored) {}
            return hce.getStatusText();
        }
        return e.getMessage();
    }

    private boolean isReachable(String baseUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl).openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("HEAD");
            int code = connection.getResponseCode();
            return code < 500;
        } catch (Exception e) {
            return false;
        }
    }
}