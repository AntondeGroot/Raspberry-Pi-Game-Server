package ADG.Lobby;

import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;

public interface RoomServiceAsync {
    void getRooms(AsyncCallback<ArrayList<Room>> asyncCallback);

    void getRoomById(String roomId, AsyncCallback<Room> asyncCallback);

    void createRoom(Room room, AsyncCallback<Room> asyncCallback);

    void deleteRoom(String name, AsyncCallback<Void> asyncCallback);

    void updateRoom(Room room, AsyncCallback<Void> asyncCallback);

    void addPlayerIdToRoom(String playerId, String roomId, AsyncCallback<Void> asyncCallback);

    void removePlayerFromRoom(String playerId, String roomId, AsyncCallback<Void> asyncCallback);

    void setUsernameAndProfile(Room room, String userId, String username, String profileId, AsyncCallback<Void> asyncCallback);

    void publishRoom(String roomId, AsyncCallback<Void> asyncCallback);

    void startGame(String roomId, AsyncCallback<Room> asyncCallback);

    void getAvailableGames(AsyncCallback<ArrayList<GameDefinition>> asyncCallback);

    void getGameOptions(String gameId, AsyncCallback<ArrayList<GameOption>> callback);
    void setRoomGame(String roomId, String gameId, AsyncCallback<Void> callback);
    void setRoomPermissions(String roomId, boolean anyPlayerCanSelectGame, boolean anyPlayerCanSetOptions, AsyncCallback<Void> callback);
    void setRoomPassword(String roomId, boolean enabled, AsyncCallback<Void> callback);
}
