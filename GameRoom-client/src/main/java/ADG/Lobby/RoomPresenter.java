package ADG.Lobby;

import ADG.*;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import ADG.Utils.ChatCipher;
import ADG.Utils.Cookie;
import ADG.Utils.EventSourceWrapper;
import ADG.Utils.GameTranslations;
import ADG.Utils.TimeUtils;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RoomPresenter implements Presenter {

    private static final String CHAT_BASE_URL = "";

    private final RoomServiceAsync roomService;
    private final RoomView roomView;
    private int chatMessageCount = 0;
    private final Room room;
    private final PresenterManager presenterManager;
    private HashMap<String, String> userNames = new HashMap<>();
    private HashMap<String, String> userProfiles = new HashMap<>();
    private boolean playerListInitialized = false;
    private boolean isAdmin = false;
    private String knownGameId = null;
    private GameStatus lastKnownStatus = null;
    private ArrayList<GameDefinition> availableGames = new ArrayList<>();
    private ArrayList<GameOption> gameOptionDefs = new ArrayList<>();
    private HashMap<String, String> knownGameOptions = new HashMap<>();
    private boolean knownAnyPlayerCanSelectGame = false;
    private boolean knownAnyPlayerCanSetOptions = false;
    private boolean knownPasswordRequired = false;
    private final EventSourceWrapper sseWrapper = new EventSourceWrapper();
    private final EventSourceWrapper chatSseWrapper = new EventSourceWrapper();
    private final List<HandlerRegistration> handlerRegistrations = new ArrayList<>();

    public RoomPresenter(RoomView roomView, Room model, PresenterManager presenterManager, RoomServiceAsync roomService, MessageServiceAsync messageService) {
        this.roomView = roomView;
        this.room = model;
        this.presenterManager = presenterManager;
        this.roomService = roomService;
    }

    @Override
    public void start() {
        History.newItem("room=" + room.getId());
        AudioPlayer.play(AudioPlayer.PLAYER_ENTER);
        knownGameId = room.getGameId();
        knownGameOptions = room.getGameOptions() != null ? new HashMap<>(room.getGameOptions()) : new HashMap<>();
        knownAnyPlayerCanSelectGame = room.isAnyPlayerCanSelectGame();
        knownAnyPlayerCanSetOptions = room.isAnyPlayerCanSetOptions();
        knownPasswordRequired = room.hasPassword();
        loadAvailableGamesForRoom();
        checkAdminStatus();
        bind();
        sseWrapper.open(
            "/rooms/" + room.getId() + "/stream",
            this::handleRoomSseMessage,
            () -> { stop(); presenterManager.switchToLobby(); }
        );
        chatSseWrapper.open("/chat/" + room.getId() + "/stream", this::handleChatSseMessage);
    }

    @Override
    public void stop() {
        sseWrapper.close();
        chatSseWrapper.close();
        for (HandlerRegistration reg : handlerRegistrations) reg.removeHandler();
        handlerRegistrations.clear();
    }

    private void bind(){
        roomView.showRoomName(room.getName());
        roomView.refreshPlayerList(new HashMap<>(), new HashMap<>());
        roomView.refreshMessages(new ArrayList<>());
        handlerRegistrations.add(roomView.getLeaveRoomButton().addClickHandler(event -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); leaveRoom(); }));
        handlerRegistrations.add(roomView.getDeleteRoomButton().addClickHandler(event -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); deleteRoom(); }));
        handlerRegistrations.add(roomView.getStartGameButton().addClickHandler(event -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); startGame(); }));
        handlerRegistrations.add(roomView.getSendMessageButton().addClickHandler(event -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); sendMessage(); }));
        handlerRegistrations.add(roomView.getMessageInputField().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                sendMessage();
            }
        }));
        roomView.updateCreatorControls(room);
        handlerRegistrations.add(roomView.getGameSelectorBox().addChangeHandler(event -> onGameSelectorChanged()));
        handlerRegistrations.add(roomView.getOptionsButton().addClickHandler(event -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); onOptionsButtonClicked(); }));
        handlerRegistrations.add(roomView.getAnyPlayerCanSelectGameCheckbox().addValueChangeHandler(event -> onPermissionChanged()));
        handlerRegistrations.add(roomView.getAnyPlayerCanSetOptionsCheckbox().addValueChangeHandler(event -> onPermissionChanged()));
        handlerRegistrations.add(roomView.getPasswordRequiredCheckbox().addValueChangeHandler(event -> onPasswordRequiredChanged()));
    }

    private void startGame() {
        GWT.log("Starting game for room: " + room.getId());
        roomService.startGame(room.getId(), new AsyncCallback<Room>() {
            @Override
            public void onFailure(Throwable throwable) {
                String errorMsg = "Unknown error";
                if (throwable instanceof ADG.Lobby.RoomServiceException) {
                    errorMsg = throwable.getMessage();
                } else if (throwable.getMessage() != null) {
                    errorMsg = throwable.getMessage();
                }
                GWT.log("Failed to start game: " + errorMsg);
                AudioPlayer.errorAlert(errorMsg);
            }

            @Override
            public void onSuccess(Room room) {
                GWT.log("Game started successfully");
                roomView.updateCreatorControls(room);
            }
        });
    }

    private void loadAvailableGamesForRoom() {
        roomService.getAvailableGames(new AsyncCallback<ArrayList<GameDefinition>>() {
            @Override public void onFailure(Throwable t) {
                GWT.log("Failed to load games for room: " + t.getMessage());
            }
            @Override public void onSuccess(ArrayList<GameDefinition> games) {
                availableGames = games;
                // Eagerly sync embeddedSettings for the currently selected game.
                // This callback completes before the game selector is visible, so
                // room.embeddedSettings is correct by the time Options can be clicked.
                syncEmbeddedSettingsFromDefs(knownGameId);
                roomView.populateGameSelector(games);
                roomView.setSelectedGame(knownGameId);
                if (knownGameId != null) {
                    fetchGameOptionDefs(knownGameId);
                }
            }
        });
    }

    private void onGameSelectorChanged() {
        String gameId = roomView.getSelectedGameId();
        if (gameId == null || gameId.equals(knownGameId)) return;
        roomService.setRoomGame(room.getId(), gameId, new AsyncCallback<Void>() {
            @Override public void onFailure(Throwable t) {
                String msg = t instanceof RoomServiceException ? t.getMessage() : "Failed to set game";
                AudioPlayer.errorAlert(msg);
                roomView.setSelectedGame(knownGameId);
            }
            @Override public void onSuccess(Void v) {}
        });
    }

    private void onOptionsButtonClicked() {
        room.setGameOptions(new HashMap<>(knownGameOptions));
        syncEmbeddedSettingsFromDefs(knownGameId); // belt-and-suspenders: ensure flag is current
        presenterManager.switchToGameOptions(room, isAdmin);
    }

    /**
     * Looks up the GameDefinition for {@code gameId} in the already-loaded
     * {@code availableGames} list and updates {@code room.embeddedSettings}
     * accordingly. No-op when the list hasn't loaded yet or the id isn't found.
     */
    private void syncEmbeddedSettingsFromDefs(String gameId) {
        for (GameDefinition gameDef : availableGames) {
            if (Objects.equals(gameDef.getId(), gameId)) {
                GWT.log("[RoomPresenter] syncEmbeddedSettings: gameId=" + gameId
                        + " embeddedSettings=" + gameDef.isEmbeddedSettings());
                room.setEmbeddedSettings(gameDef.isEmbeddedSettings());
                return;
            }
        }
        GWT.log("[RoomPresenter] syncEmbeddedSettings: no match for gameId=" + gameId
                + " (availableGames.size=" + availableGames.size() + ")");
    }

    private void onPermissionChanged() {
        boolean anyGame = roomView.getAnyPlayerCanSelectGameCheckbox().getValue();
        boolean anyOpts = roomView.getAnyPlayerCanSetOptionsCheckbox().getValue();
        roomService.setRoomPermissions(room.getId(), anyGame, anyOpts, new AsyncCallback<Void>() {
            @Override public void onFailure(Throwable t) {
                GWT.log("Failed to set permissions: " + t.getMessage());
            }
            @Override public void onSuccess(Void v) {}
        });
    }

    private void onPasswordRequiredChanged() {
        boolean enabled = roomView.getPasswordRequiredCheckbox().getValue();
        roomService.setRoomPassword(room.getId(), enabled, new AsyncCallback<Void>() {
            @Override public void onFailure(Throwable t) {
                GWT.log("Failed to set room password: " + t.getMessage());
                roomView.getPasswordRequiredCheckbox().setValue(knownPasswordRequired, false);
            }
            @Override public void onSuccess(Void v) {}
        });
    }

    private void fetchGameOptionDefs(String gameId) {
        roomService.getGameOptions(gameId, new AsyncCallback<ArrayList<GameOption>>() {
            @Override public void onFailure(Throwable t) {
                GWT.log("Failed to fetch game options: " + t.getMessage());
            }
            @Override public void onSuccess(ArrayList<GameOption> options) {
                if (options == null) options = new ArrayList<>();
                final ArrayList<GameOption> defs = options;
                String protocol = Window.Location.getProtocol();
                String host = Window.Location.getHost();
                String baseUrl = protocol + "//" + host + "/" + gameId;
                GameTranslations.load(baseUrl, Cookie.getLanguage(), () -> {
                    if (gameId.equals(knownGameId)) {
                        gameOptionDefs = defs;
                        roomView.renderOptionsDiff(gameOptionDefs, knownGameOptions);
                    }
                });
            }
        });
    }

    private void updateGameConfigControls(Room updatedRoom) {
        boolean isCreator = updatedRoom.getCreatedByUserId() != null
                && updatedRoom.getCreatedByUserId().equals(Cookie.getPlayerId());
        boolean canSelectGame = isCreator || updatedRoom.isAnyPlayerCanSelectGame();
        boolean canSetOptions = isCreator || updatedRoom.isAnyPlayerCanSetOptions();
        roomView.setGameSelectorEnabled(canSelectGame);
        roomView.setOptionsButtonEnabled(canSetOptions && knownGameId != null);
        roomView.setPermissionsVisible(isCreator);
        roomView.getAnyPlayerCanSelectGameCheckbox().setValue(updatedRoom.isAnyPlayerCanSelectGame(), false);
        roomView.getAnyPlayerCanSetOptionsCheckbox().setValue(updatedRoom.isAnyPlayerCanSetOptions(), false);
        roomView.getPasswordRequiredCheckbox().setValue(updatedRoom.hasPassword(), false);
    }

    private void sendMessageToServer(String inputText) {
        String encrypted = ChatCipher.encrypt(inputText, room.getId());
        String sender = userNames.getOrDefault(Cookie.getPlayerId(), "?");
        JSONObject body = new JSONObject();
        body.put("sender", new JSONString(sender));
        body.put("message", new JSONString(encrypted));
        try {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.POST,
                    URL.encode(CHAT_BASE_URL + "/chat/" + room.getId()));
            rb.setHeader("Content-Type", "application/json");
            rb.sendRequest(body.toString(), new RequestCallback() {
                @Override public void onResponseReceived(Request req, Response res) {}
                @Override public void onError(Request req, Throwable ex) {}
            });
        } catch (RequestException e) {
            GWT.log("chat send error: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String inputText = roomView.getMessageInputField().getText();
        if(!inputText.isEmpty()){
            sendMessageToServer(inputText);
            roomView.getMessageInputField().setText("");
        }
    }

    private void leaveRoom() {
        if (room.getStatus() == GameStatus.PLAYING) {
            if (!Window.confirm(I18n.c().confirmLeavePlayingRoom())) return;
        }
        removePlayerFromRoom();
        sendLeaveMessage();
        presenterManager.switchToLobby();
    }

    private void sendLeaveMessage() {
        sendMessageToServer(I18n.c().hasLeftTheRoom());
    }

    private void deleteRoom() {
        boolean confirmDelete = Window.confirm(I18n.c().confirmDeleteRoom());
        if (!confirmDelete) return;
        if (isAdmin) {
            // Admins delete via the Spring Security-protected REST endpoint.
            deleteRoomAsAdmin();
        } else {
            // Room creators delete via GWT-RPC. This path is NOT protected by Spring Security;
            // authorization is enforced server-side in RoomServiceImpl by checking the playerid cookie.
            roomService.deleteRoom(room.getId(), new AsyncCallback<Void>() {
                @Override public void onFailure(Throwable throwable) {}
                @Override public void onSuccess(Void unused) { presenterManager.switchToLobby(); }
            });
        }
    }

    private void deleteRoomAsAdmin() {
        try {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.DELETE, "/admin/rooms/" + room.getId());
            rb.setHeader("Accept", "application/json");
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_NO_CONTENT) {
                        presenterManager.switchToLobby();
                    } else {
                        Window.alert(I18n.m().errDeleteFailedHttp(response.getStatusCode()));
                    }
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    Window.alert(I18n.m().errDeleteFailed(exception.getMessage()));
                }
            });
        } catch (RequestException e) {
            GWT.log("Admin delete room failed: " + e.getMessage());
        }
    }

    private void checkAdminStatus() {
        try {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, "/admin/check");
            rb.setHeader("Accept", "application/json");
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_OK) {
                        isAdmin = true;
                        roomView.setAdminMode(true);
                    }
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    GWT.log("Admin check error: " + exception.getMessage());
                }
            });
        } catch (RequestException e) {
            GWT.log("Admin check failed: " + e.getMessage());
        }
    }

    private void handleRoomSseMessage(String data) {
        try {
            handleRoomSseMessageInternal(data);
        } catch (Exception e) {
            GWT.log("Room SSE parse error: " + e.getMessage());
        }
    }

    private void handleRoomSseMessageInternal(String data) {
        JSONValue parsed = JSONParser.parseStrict(data);
        JSONObject obj = parsed.isObject();
        if (obj == null) return;

        Room updatedRoom = parseRoom(obj);

        boolean gameJustStarted = lastKnownStatus != null
                && lastKnownStatus != GameStatus.PLAYING
                && updatedRoom.getStatus() == GameStatus.PLAYING;
        lastKnownStatus = updatedRoom.getStatus();

        if (gameJustStarted && updatedRoom.getGameSessionId() != null
                && updatedRoom.getPlayerIds().contains(Cookie.getPlayerId())) {
            stop();
            String url = updatedRoom.getGameBaseUrl()
                    + "/?sessionid=" + updatedRoom.getGameSessionId()
                    + "&playerid=" + Cookie.getPlayerId()
                    + "&locale=" + Cookie.getLanguage().name()
                    + "&roomid=" + updatedRoom.getId();
            GWT.log("Navigating to game URL: " + url);
            Window.Location.replace(url);
            return;
        }

        if (updatedRoom.getStatus() == GameStatus.PLAYING) {
            roomView.getLeaveRoomButton().setVisible(false);
        }

        HashMap<String, String> serverUserNames = updatedRoom.getPlayerNames();
        HashMap<String, String> serverUserProfiles = updatedRoom.getPlayerProfiles();
        if (!serverUserNames.equals(userNames) || !serverUserProfiles.equals(userProfiles)) {
            if (playerListInitialized && serverUserNames.size() > userNames.size()) {
                AudioPlayer.play(AudioPlayer.PLAYER_ENTER);
            }
            playerListInitialized = true;
            userNames = serverUserNames;
            userProfiles = serverUserProfiles;
            roomView.refreshPlayerList(userNames, userProfiles);
        } else {
            playerListInitialized = true;
        }
        // ── Game config ──────────────────────────────────────────────
        String newGameId = updatedRoom.getGameId();
        HashMap<String, String> newGameOptions = updatedRoom.getGameOptions() != null
                ? updatedRoom.getGameOptions() : new HashMap<>();

        if (!Objects.equals(newGameId, knownGameId)) {
            knownGameId = newGameId;
            knownGameOptions = new HashMap<>();
            gameOptionDefs.clear();
            roomView.setSelectedGame(newGameId);
            roomView.renderOptionsDiff(gameOptionDefs, knownGameOptions);
            if (newGameId != null) {
                fetchGameOptionDefs(newGameId);
            }
        } else if (!newGameOptions.equals(knownGameOptions)) {
            knownGameOptions = new HashMap<>(newGameOptions);
            roomView.renderOptionsDiff(gameOptionDefs, knownGameOptions);
        }

        // Keep this.room in sync for GameOptionsPresenter
        room.setGameId(newGameId);
        room.setGameOptions(new HashMap<>(newGameOptions));
        room.setGameBaseUrl(updatedRoom.getGameBaseUrl());
        room.setMinPlayers(updatedRoom.getMinPlayers());
        room.setMaxPlayers(updatedRoom.getMaxPlayers());
        room.setAnyPlayerCanSelectGame(updatedRoom.isAnyPlayerCanSelectGame());
        room.setAnyPlayerCanSetOptions(updatedRoom.isAnyPlayerCanSetOptions());
        room.setRoomPassword(updatedRoom.getRoomPassword());
        room.setEmbeddedSettings(updatedRoom.isEmbeddedSettings());
        knownPasswordRequired = updatedRoom.hasPassword();
        roomView.updatePasswordDisplay(updatedRoom.getRoomPassword());

        updateGameConfigControls(updatedRoom);
        roomView.updateCreatorControls(updatedRoom);
    }

    private Room parseRoom(JSONObject obj) {
        Room r = new Room();
        r.setId(str(obj, "id"));
        r.setName(str(obj, "name"));
        r.setCreatedByUserId(str(obj, "createdByUserId"));
        r.setGameId(str(obj, "gameId"));
        r.setGameSessionId(str(obj, "gameSessionId"));
        r.setGameBaseUrl(str(obj, "gameBaseUrl"));

        JSONValue statusVal = obj.get("status");
        if (statusVal != null && statusVal.isString() != null) {
            try { r.setStatus(GameStatus.valueOf(statusVal.isString().stringValue())); }
            catch (Exception ignored) { r.setStatus(GameStatus.WAITING); }
        }
        JSONValue maxVal = obj.get("maxPlayers");
        if (maxVal != null && maxVal.isNumber() != null)
            r.setMaxPlayers((int) maxVal.isNumber().doubleValue());
        JSONValue minVal = obj.get("minPlayers");
        if (minVal != null && minVal.isNumber() != null)
            r.setMinPlayers((int) minVal.isNumber().doubleValue());

        JSONValue playerIdsVal = obj.get("playerIds");
        if (playerIdsVal != null && playerIdsVal.isArray() != null) {
            JSONArray arr = playerIdsVal.isArray();
            for (int i = 0; i < arr.size(); i++) {
                JSONValue v = arr.get(i);
                if (v != null && v.isString() != null) r.addPlayer(v.isString().stringValue());
            }
        }
        for (Map.Entry<String, String> e : parseStringMap(obj.get("playerNames")).entrySet())
            r.addPlayerName(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : parseStringMap(obj.get("playerProfiles")).entrySet())
            r.addPlayerProfile(e.getKey(), e.getValue());
        // gameOptions
        JSONValue gameOptsVal = obj.get("gameOptions");
        if (gameOptsVal != null) {
            r.setGameOptions(parseStringMap(gameOptsVal));
        }
        // permission flags
        JSONValue anySelectVal = obj.get("anyPlayerCanSelectGame");
        if (anySelectVal != null && anySelectVal.isBoolean() != null) {
            r.setAnyPlayerCanSelectGame(anySelectVal.isBoolean().booleanValue());
        }
        JSONValue anyOptsVal = obj.get("anyPlayerCanSetOptions");
        if (anyOptsVal != null && anyOptsVal.isBoolean() != null) {
            r.setAnyPlayerCanSetOptions(anyOptsVal.isBoolean().booleanValue());
        }
        JSONValue pwdVal = obj.get("roomPassword");
        if (pwdVal != null && pwdVal.isString() != null) {
            r.setRoomPassword(pwdVal.isString().stringValue());
        }
        JSONValue embeddedVal = obj.get("embeddedSettings");
        if (embeddedVal != null && embeddedVal.isBoolean() != null) {
            r.setEmbeddedSettings(embeddedVal.isBoolean().booleanValue());
        }
        return r;
    }

    private String str(JSONObject obj, String key) {
        JSONValue v = obj.get(key);
        if (v == null || v.isString() == null) return null;
        return v.isString().stringValue();
    }

    private HashMap<String, String> parseStringMap(JSONValue val) {
        HashMap<String, String> map = new HashMap<>();
        if (val == null || val.isObject() == null) return map;
        JSONObject jsonObj = val.isObject();
        for (String key : jsonObj.keySet()) {
            JSONValue v = jsonObj.get(key);
            if (v != null && v.isString() != null) map.put(key, v.isString().stringValue());
        }
        return map;
    }

    private void handleChatSseMessage(String data) {
        try {
            JSONValue parsed = JSONParser.parseStrict(data);
            JSONArray arr = parsed.isArray();
            if (arr == null || arr.size() == chatMessageCount) return;
            chatMessageCount = arr.size();
            ArrayList<Message> decrypted = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject m = arr.get(i).isObject();
                if (m == null) continue;
                String timestampUTC = m.get("timestampUTC").isString().stringValue();
                String sender       = m.get("sender").isString().stringValue();
                String encrypted    = m.get("message").isString().stringValue();
                decrypted.add(new Message(TimeUtils.convertUTCToLocal(timestampUTC), sender,
                        ChatCipher.decrypt(encrypted, room.getId())));
            }
            roomView.refreshMessages(decrypted);
        } catch (Exception e) {
            GWT.log("Chat SSE parse error: " + e.getMessage());
        }
    }

    private void removePlayerFromRoom() {
        roomService.removePlayerFromRoom(Cookie.getPlayerId(), room.getId(), new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable throwable) {
            }

            @Override
            public void onSuccess(Void v) {
            }
        });
    }
}
