package ADG.i18n;

import com.google.gwt.i18n.client.Messages;

public interface AppMessages extends Messages {
    String waitingForPlayers(int count);
    String willStartTheGame(String name);
    String confirmLeaveRoom(String roomName);
    String confirmDeleteRoomNamed(String roomName);
    String confirmRemovePlayer(String playerName);
    String errMaxPlayersBetween(int min, int max);
    String errDeleteFailedHttp(int statusCode);
    String errDeleteFailed(String message);
    String errCouldNotCreateRoom(String message);
    String errFailedToJoinRoom(String message);
    String errFailedToSetProfile(String message);
    String errFailedToOpenRoom(String message);
    String errFailedToSaveOptions(String message);
}