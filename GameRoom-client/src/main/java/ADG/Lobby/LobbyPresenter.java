package ADG.Lobby;

import ADG.*;
import ADG.Utils.Cookie;
import ADG.Utils.EventSourceWrapper;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.HashMap;

public class LobbyPresenter implements Presenter {

    private final LobbyView view;
    private final PresenterManager presenterManager;
    private final RoomServiceAsync roomService;
    private final ArrayList<Room> rooms = new ArrayList<>();
    private final EventSourceWrapper lobbySse = new EventSourceWrapper();
    private ArrayList<GameOption> cachedGameOptions = null;
    private String selectedGameId = null;
    private String pendingRejoinRoomId = null;

    @Override
    public void start() {
        History.newItem("");
        pendingRejoinRoomId = Window.Location.getParameter("rejoin");
        checkAdminStatus();
        loadAvailableGames();
        lobbySse.open("/lobby/stream", this::handleLobbySseMessage);
    }

    @Override
    public void stop() {
        lobbySse.close();
    }

    public LobbyPresenter(LobbyView view, PresenterManager presenterManager, RoomServiceAsync roomService) {
        this.view = view;
        this.presenterManager = presenterManager;
        this.roomService = roomService;
        bind();
    }

    private void bind() {
        view.setCurrentPlayerId(Cookie.getPlayerId());
        view.getCreateRoomTitle().addClickHandler(event -> view.toggleCreateRoom());
        view.addGameSelectionChangeHandler(e -> onGameSelectionChanged());
        view.setDeleteHandler(this::deleteRoomAsAdmin);
        view.setRemovePlayerHandler(this::removePlayerAsAdmin);
        view.getCreateRoomButton().addClickHandler(event -> {
            String roomName = view.getRoomNameInput().getText().trim();
            if (roomName.isEmpty()) {
                AudioPlayer.errorAlert(I18n.c().errRoomNameEmpty());
                return;
            }
            if (roomName.length() < 3) {
                AudioPlayer.errorAlert(I18n.c().errRoomNameTooShort());
                return;
            }
            if (roomName.length() > 25) {
                AudioPlayer.errorAlert(I18n.c().errRoomNameTooLong());
                return;
            }
            AudioPlayer.play(AudioPlayer.BUTTON_CLICK);
            createRoom(roomName);
        });
        view.getRandomNameButton().addClickHandler(event -> fetchRandomRoomName());
        view.setJoinHandler(room -> {
            if (GameStatus.PLAYING.equals(room.getStatus())) {
                presenterManager.switchToGameRoom(room);
            } else {
                navigateToCharacterSelection(room);
            }
        });
    }

    private void fetchRandomRoomName() {
        RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, "/random-room-name");
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == 200) {
                        String name = response.getText().replaceAll(".*\"name\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                        view.getRoomNameInput().setText(name);
                    }
                }
                @Override public void onError(Request request, Throwable exception) {}
            });
        } catch (RequestException e) {
            GWT.log("Failed to fetch random room name: " + e.getMessage());
        }
    }

    private void updateRoomTable() {
        view.updateRoomTable(rooms);
    }

    private void handleLobbySseMessage(String data) {
        try {
            ArrayList<Room> fetchedRooms = parseRooms(data);
            if (!rooms.equals(fetchedRooms)) {
                updateRooms(fetchedRooms);
                updateRoomTable();
            }
            if (pendingRejoinRoomId != null) {
                attemptAutoRejoin();
            }
        } catch (Exception e) {
            GWT.log("Lobby SSE parse error: " + e.getMessage());
        }
    }

    private void attemptAutoRejoin() {
        final String roomId = pendingRejoinRoomId;
        Room room = rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElse(null);
        if (room == null) return;
        pendingRejoinRoomId = null;
        if (GameStatus.PLAYING.equals(room.getStatus())) {
            presenterManager.switchToGameRoom(room);
        } else {
            navigateToCharacterSelection(room);
        }
    }

    private ArrayList<Room> parseRooms(String data) {
        ArrayList<Room> result = new ArrayList<>();
        JSONValue parsed = JSONParser.parseStrict(data);
        JSONArray arr = parsed.isArray();
        if (arr == null) return result;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject obj = arr.get(i).isObject();
            if (obj != null) result.add(parseRoom(obj));
        }
        return result;
    }

    private Room parseRoom(JSONObject obj) {
        Room r = new Room();
        r.setId(str(obj, "id"));
        r.setName(str(obj, "name"));
        r.setCreatedByUserId(str(obj, "createdByUserId"));
        r.setGameId(str(obj, "gameId"));

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
        for (java.util.Map.Entry<String, String> e : parseStringMap(obj.get("playerNames")).entrySet())
            r.addPlayerName(e.getKey(), e.getValue());
        for (java.util.Map.Entry<String, String> e : parseStringMap(obj.get("playerProfiles")).entrySet())
            r.addPlayerProfile(e.getKey(), e.getValue());
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
        JSONObject obj = val.isObject();
        for (String key : obj.keySet()) {
            JSONValue v = obj.get(key);
            if (v != null && v.isString() != null) map.put(key, v.isString().stringValue());
        }
        return map;
    }

    private void onGameSelectionChanged() {
        String gameId = view.getSelectedGameId();
        if (gameId == null || gameId.equals(selectedGameId)) return;
        selectedGameId = gameId;
        cachedGameOptions = null;
        roomService.getGameOptions(gameId, new AsyncCallback<ArrayList<GameOption>>() {
            @Override public void onFailure(Throwable t) {
                GWT.log("Failed to load game options: " + t.getMessage());
            }
            @Override public void onSuccess(ArrayList<GameOption> options) {
                if (gameId.equals(selectedGameId)) cachedGameOptions = options;
            }
        });
    }

    private void navigateToGameOptions(Room room) {
        presenterManager.switchToGameOptions(room, cachedGameOptions);
    }

    /**
     * Navigate to the selected room.
     */
    private void navigateToCharacterSelection(Room room) {
        addPlayerIdToRoom(room);
        presenterManager.switchToCharacterSelection(room);
    }

    private void addPlayerIdToRoom(Room room){
        roomService.addPlayerIdToRoom(Cookie.getPlayerId(), room.getId(), new AsyncCallback<Void>() {
            @Override
            public void onFailure(Throwable throwable) {
                GWT.log("Failed to add user to room");
            }

            @Override
            public void onSuccess(Void v) {
                GWT.log("Succesfully added user to room");
            }
        });
    }

    private boolean isRoomNameValid(String roomName) {
        return roomName != null && !roomName.trim().isEmpty() && roomName.trim().length() <= 25;
    }

    private void loadAvailableGames() {
        roomService.getAvailableGames(new AsyncCallback<ArrayList<GameDefinition>>() {
            @Override
            public void onFailure(Throwable throwable) {
                GWT.log("Failed to load available games: " + throwable.getMessage());
            }

            @Override
            public void onSuccess(ArrayList<GameDefinition> games) {
                view.populateGameList(games);
                onGameSelectionChanged();
            }
        });
    }

    private void createRoom(String roomName) {
        String gameId = view.getSelectedGameId();
        if (gameId == null) {
            AudioPlayer.errorAlert(I18n.c().errSelectGame());
            return;
        }
        // Fast client-side check against cached list before hitting the server
        boolean nameExists = rooms.stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(roomName));
        if (nameExists) {
            AudioPlayer.errorAlert(I18n.c().errRoomNameExists());
            return;
        }
        String playerId = Cookie.getPlayerId();
        Room currentRoom = rooms.stream()
                .filter(r -> r.getPlayerIds().contains(playerId))
                .findFirst().orElse(null);
        if (currentRoom != null) {
            boolean confirmed = Window.confirm(I18n.m().confirmLeaveRoom(currentRoom.getName()));
            if (!confirmed) return;
        }
        Room room = new Room(roomName, playerId);
        room.setGameId(gameId);
        view.getRoomNameInput().setText("");
        roomService.createRoom(room, new AsyncCallback<Room>() {
            @Override
            public void onFailure(Throwable t) {
                view.showAlert(I18n.m().errCouldNotCreateRoom(t.getMessage()));
            }
            @Override
            public void onSuccess(Room created) {
                navigateToGameOptions(created);
            }
        });
    }

    private synchronized void updateRooms(ArrayList<Room> fetchedRooms) {
        rooms.clear();
        rooms.addAll(fetchedRooms);
    }

    private void checkAdminStatus() {
        RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, "/admin/check");
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_OK) {
                        view.setAdminMode(true);
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

    private void removePlayerAsAdmin(Room room, String playerId, String playerName) {
        if (!Window.confirm(I18n.m().confirmRemovePlayer(playerName))) return;
        RequestBuilder rb = new RequestBuilder(RequestBuilder.DELETE,
                "/admin/rooms/" + room.getId() + "/players/" + playerId);
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() != Response.SC_NO_CONTENT) {
                        view.showAlert(I18n.m().errDeleteFailed(response.getStatusText()));
                    }
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    view.showAlert(I18n.m().errDeleteFailed(exception.getMessage()));
                }
            });
        } catch (RequestException e) {
            GWT.log("Remove player failed: " + e.getMessage());
        }
    }

    private void deleteRoomAsAdmin(Room room) {
        if (!Window.confirm(I18n.m().confirmDeleteRoomNamed(room.getName()))) return;
        RequestBuilder rb = new RequestBuilder(RequestBuilder.DELETE, "/admin/rooms/" + room.getId());
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == Response.SC_NO_CONTENT) {
                        // Table updates on next poll automatically
                    } else if (response.getStatusCode() == Response.SC_UNAUTHORIZED
                               || response.getStatusCode() == Response.SC_FORBIDDEN) {
                        view.showAlert(I18n.c().errNotAuthorised());
                    } else {
                        view.showAlert(I18n.m().errDeleteFailedHttp(response.getStatusCode()));
                    }
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    view.showAlert(I18n.m().errDeleteFailed(exception.getMessage()));
                }
            });
        } catch (RequestException e) {
            GWT.log("Delete room failed: " + e.getMessage());
        }
    }
}
