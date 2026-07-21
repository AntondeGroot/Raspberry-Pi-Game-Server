package ADG.Lobby;

import ADG.*;
import ADG.Utils.ConfirmDialog;
import ADG.Utils.Cookie;
import ADG.Utils.EventSourceWrapper;
import ADG.Utils.PasswordPromptDialog;
import ADG.Utils.RoomPasswordStore;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class LobbyPresenter implements Presenter {

    private final LobbyView view;
    private final PresenterManager presenterManager;
    private final RoomServiceAsync roomService;
    private final ArrayList<Room> rooms = new ArrayList<>();
    private final EventSourceWrapper lobbySse = new EventSourceWrapper();
    private String pendingRejoinRoomId = null;
    private boolean initialLobbyLoadDone = false;

    @Override
    public void start() {
        History.newItem("");
        pendingRejoinRoomId = Window.Location.getParameter("rejoin");
        view.showLoadingRooms();
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
        view.setDeleteHandler(this::deleteRoomAsAdmin);
        view.setRemovePlayerHandler(this::removePlayerAsAdmin);
        view.getAdminLogsButton().addClickHandler(event -> presenterManager.switchToAdmin());
        view.getAdminLogoutButton().addClickHandler(event -> logoutAsAdmin());
        view.getCreateRoomButton().addClickHandler(event -> {
            String roomName = view.getRoomNameInput().getText().trim();
            if (roomName.isEmpty()) {
                // No name entered — pick a random name that isn't already taken and
                // create the room with it, so a player who doesn't care about the name
                // can just make a room.
                AudioPlayer.play(AudioPlayer.BUTTON_CLICK);
                pickRandomAvailableName(name -> {
                    view.getRoomNameInput().setText(name);
                    createRoom(name);
                });
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
        // The random-name button only fills the input, so a player can keep clicking
        // until they see a name they like without creating the room.
        view.getRandomNameButton().addClickHandler(event ->
                pickRandomAvailableName(name -> view.getRoomNameInput().setText(name)));
        view.setJoinHandler(room -> {
            if (GameStatus.PLAYING.equals(room.getStatus())) {
                presenterManager.switchToGameRoom(room);
            } else if (room.hasPassword()) {
                new PasswordPromptDialog().show(
                        room.getName(),
                        RoomPasswordStore.get(room.getId()),
                        entered -> entered.equalsIgnoreCase(room.getRoomPassword()),
                        () -> {
                            // Remember it so a removed player can re-enter later,
                            // even once the room is empty.
                            RoomPasswordStore.put(room.getId(), room.getRoomPassword());
                            navigateToCharacterSelection(room);
                        });
            } else {
                navigateToCharacterSelection(room);
            }
        });
    }

    /**
     * Picks a random room name that isn't already taken by an existing room and
     * passes it to {@code onName}. Fetches the full pool of candidate names, removes
     * the ones currently in use, and chooses randomly from what's left. If every
     * name is taken, shows a message asking the player to enter one themselves.
     */
    private void pickRandomAvailableName(Consumer<String> onName) {
        fetchRoomNames(allNames -> {
            Set<String> taken = new HashSet<>();
            for (Room r : rooms) {
                if (r.getName() != null) taken.add(r.getName().trim().toLowerCase());
            }
            List<String> available = new ArrayList<>();
            for (String n : allNames) {
                if (!taken.contains(n.trim().toLowerCase())) available.add(n);
            }
            if (available.isEmpty()) {
                AudioPlayer.errorAlert(I18n.c().errNoRandomRoomNames());
                return;
            }
            onName.accept(available.get(Random.nextInt(available.size())));
        });
    }

    private void fetchRoomNames(Consumer<List<String>> onNames) {
        RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, "/room-names");
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    if (response.getStatusCode() == 200) {
                        onNames.accept(parseNameArray(response.getText()));
                    }
                }
                @Override public void onError(Request request, Throwable exception) {
                    GWT.log("Failed to fetch room names");
                }
            });
        } catch (RequestException e) {
            GWT.log("Failed to fetch room names: " + e.getMessage());
        }
    }

    private List<String> parseNameArray(String json) {
        List<String> result = new ArrayList<>();
        try {
            JSONValue parsed = JSONParser.parseStrict(json);
            JSONArray arr = parsed.isArray();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONValue v = arr.get(i);
                    if (v != null && v.isString() != null) result.add(v.isString().stringValue());
                }
            }
        } catch (Exception e) {
            GWT.log("Failed to parse room names: " + e.getMessage());
        }
        return result;
    }

    private void updateRoomTable() {
        view.updateRoomTable(rooms);
    }

    private void handleLobbySseMessage(String data) {
        try {
            ArrayList<Room> fetchedRooms = parseRooms(data);
            boolean changed = !rooms.equals(fetchedRooms);
            if (changed) {
                updateRooms(fetchedRooms);
            }
            // Always render on the first message — even an unchanged/empty list must
            // replace the loading skeleton with the real (possibly empty) table.
            if (changed || !initialLobbyLoadDone) {
                updateRoomTable();
            }
            initialLobbyLoadDone = true;
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
        JSONValue pwdVal = obj.get("roomPassword");
        if (pwdVal != null && pwdVal.isString() != null) {
            r.setRoomPassword(pwdVal.isString().stringValue());
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
        JSONObject obj = val.isObject();
        for (String key : obj.keySet()) {
            JSONValue v = obj.get(key);
            if (v != null && v.isString() != null) map.put(key, v.isString().stringValue());
        }
        return map;
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
            }
        });
    }

    private void createRoom(String roomName) {
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
        Runnable proceed = () -> {
            Room room = new Room(roomName, playerId);
            view.getRoomNameInput().setText("");
            roomService.createRoom(room, new AsyncCallback<Room>() {
                @Override
                public void onFailure(Throwable t) {
                    view.showAlert(I18n.m().errCouldNotCreateRoom(t.getMessage()));
                }
                @Override
                public void onSuccess(Room created) {
                    navigateToCharacterSelection(created);
                }
            });
        };
        if (currentRoom != null) {
            ConfirmDialog.show(I18n.m().confirmLeaveRoom(currentRoom.getName()), proceed);
        } else {
            proceed.run();
        }
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
                        // Re-render only if rooms have loaded — otherwise the skeleton is
                        // still showing and the SSE handler will render with admin mode on.
                        if (initialLobbyLoadDone) updateRoomTable();
                    } else if (Cookie.hasAdminHint()) {
                        // Session expired but this browser has logged in before — show the shortcut
                        view.showAdminLoginHintButton();
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
        ConfirmDialog.danger(I18n.m().confirmRemovePlayer(playerName), I18n.c().confirm(), () -> {
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
        });
    }

    /**
     * Logs the admin out via POST /logout (POST prevents logout CSRF), then reloads
     * the lobby so it comes back in logged-out state. Reloads even on error so the
     * user isn't stuck if the request fails.
     */
    private void logoutAsAdmin() {
        RequestBuilder rb = new RequestBuilder(RequestBuilder.POST, "/logout");
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    Window.Location.assign("/");
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    Window.Location.assign("/");
                }
            });
        } catch (RequestException e) {
            GWT.log("Logout failed: " + e.getMessage());
        }
    }

    private void deleteRoomAsAdmin(Room room) {
        ConfirmDialog.danger(I18n.m().confirmDeleteRoomNamed(room.getName()), I18n.c().deleteRoom(), () -> {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.DELETE, "/admin/rooms/" + room.getId());
            rb.setHeader("Accept", "application/json");
            try {
                rb.sendRequest(null, new RequestCallback() {
                    @Override
                    public void onResponseReceived(Request request, Response response) {
                        // 204 No Content: success — the lobby SSE stream will push the updated list.
                        if (response.getStatusCode() == Response.SC_UNAUTHORIZED
                                || response.getStatusCode() == Response.SC_FORBIDDEN) {
                            view.showAlert(I18n.c().errNotAuthorised());
                        } else if (response.getStatusCode() != Response.SC_NO_CONTENT) {
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
        });
    }
}
