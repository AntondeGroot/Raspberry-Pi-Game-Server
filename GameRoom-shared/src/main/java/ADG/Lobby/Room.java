package ADG.Lobby;

import ADG.UUID;
import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class Room implements IsSerializable {
    private String id;
    private String createdByUserId;
    private String name;
    private GameStatus status;
    private ArrayList<String> playerIds = new ArrayList<>();
    private HashMap<String, String> playerProfiles = new HashMap<>(); // Map of playerId to profileId
    private HashMap<String, String> playerNames = new HashMap<>(); // Map of playerId to userName
    private String gameSessionId;
    private String gameBaseUrl;
    private String gameId;
    private int minPlayers = 1;
    private int maxPlayers = 8;
    private boolean uniqueProfilePics = true;
    private HashMap<String, String> gameOptions = new HashMap<>();
    private boolean anyPlayerCanSelectGame = false;
    private boolean anyPlayerCanSetOptions = false;
    private String roomPassword = null;

    public Room() {} // Default constructor

    public Room(String name, String createdByUserId) {
        setCreatedByUserId(createdByUserId);
        setName(name);
        setId(UUID.get());
        status = GameStatus.WAITING;
    }

    public void addPlayer(String playerId) {
        if (!playerIds.contains(playerId)) {
            playerIds.add(playerId);
        }
    }

    public void addPlayerName(String playerId, String name) {
        playerNames.put(playerId, name);
    }

    public void addPlayerProfile(String playerId, String profile) {
        playerProfiles.put(playerId, profile);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void removePlayer(String playerId) {
        playerIds.remove(playerId);
        playerNames.remove(playerId);
        playerProfiles.remove(playerId);
    }

    public String getName() {
        return name;
    }

    public GameStatus getStatus() {return status;}

    public void setStatus(GameStatus status) {this.status = status;}

    public void setName(String name) {
        this.name = name;
    }

    public Integer getNrOfPlayers() {
        return playerIds.size();
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public HashMap<String, String> getPlayerNames() {
        return playerNames;
    }

    public HashMap<String, String> getPlayerProfiles() {
        return playerProfiles;
    }

    public ArrayList<String> getPlayerIds() {
        return playerIds;
    }

    public String getGameSessionId() {
        return gameSessionId;
    }

    public void setGameSessionId(String gameSessionId) {
        this.gameSessionId = gameSessionId;
    }

    public String getGameBaseUrl() {
        return gameBaseUrl;
    }

    public void setGameBaseUrl(String gameBaseUrl) {
        this.gameBaseUrl = gameBaseUrl;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isUniqueProfilePics() {
        return uniqueProfilePics;
    }

    public void setUniqueProfilePics(boolean uniqueProfilePics) {
        this.uniqueProfilePics = uniqueProfilePics;
    }

    public HashMap<String, String> getGameOptions() {
        return gameOptions;
    }

    public void setGameOptions(HashMap<String, String> gameOptions) {
        this.gameOptions = gameOptions;
    }

    public boolean isAnyPlayerCanSelectGame() { return anyPlayerCanSelectGame; }
    public void setAnyPlayerCanSelectGame(boolean v) { this.anyPlayerCanSelectGame = v; }

    public boolean isAnyPlayerCanSetOptions() { return anyPlayerCanSetOptions; }
    public void setAnyPlayerCanSetOptions(boolean v) { this.anyPlayerCanSetOptions = v; }

    public String getRoomPassword() { return roomPassword; }
    public void setRoomPassword(String roomPassword) { this.roomPassword = roomPassword; }
    public boolean hasPassword() { return roomPassword != null && !roomPassword.isEmpty(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return minPlayers == room.minPlayers
                && maxPlayers == room.maxPlayers
                && uniqueProfilePics == room.uniqueProfilePics
                && Objects.equals(id, room.id)
                && Objects.equals(createdByUserId, room.createdByUserId)
                && Objects.equals(name, room.name)
                && Objects.equals(status, room.status)
                && Objects.equals(playerIds, room.playerIds)
                && Objects.equals(playerNames, room.playerNames)
                && Objects.equals(playerProfiles, room.playerProfiles)
                && Objects.equals(gameId, room.gameId)
                && Objects.equals(gameSessionId, room.gameSessionId)
                && Objects.equals(gameBaseUrl, room.gameBaseUrl)
                && Objects.equals(gameOptions, room.gameOptions)
                && anyPlayerCanSelectGame == room.anyPlayerCanSelectGame
                && anyPlayerCanSetOptions == room.anyPlayerCanSetOptions
                && Objects.equals(roomPassword, room.roomPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdByUserId, name, status, playerIds, playerNames,
                playerProfiles, gameId, gameSessionId, gameBaseUrl, minPlayers, maxPlayers, uniqueProfilePics, gameOptions,
                anyPlayerCanSelectGame, anyPlayerCanSetOptions, roomPassword);
    }

    @Override
    public String toString() {
        return "Room{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", playerIds=" + playerIds +
                ", userNames=" + playerNames +
                '}';
    }
}
