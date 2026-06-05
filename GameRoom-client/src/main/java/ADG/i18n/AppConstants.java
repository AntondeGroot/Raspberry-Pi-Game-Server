package ADG.i18n;

import com.google.gwt.i18n.client.Constants;

public interface AppConstants extends Constants {

    // Lobby
    String gameLobby();
    String createARoom();
    String roomName();
    String selectGame();
    String createRoom();
    String randomName();
    String availableRooms();

    // Table headers
    String colRoomName();
    String colGame();
    String colPlayers();
    String colStatus();

    // Table row actions & status
    String rejoin();
    String join();
    String statusPlaying();
    String statusWaiting();
    String statusFull();

    // Room view
    String startGame();
    String leaveRoom();
    String deleteRoom();
    String send();
    String roomPrefix();
    String theHost();
    String hasLeftTheRoom();
    String selectGamePlaceholder();
    String optionsButtonLabel();
    String anyPlayerCanSelectGame();
    String anyPlayerCanSetOptions();
    String requirePassword();
    String enterPasswordPrompt();
    String wrongPassword();

    // Character selection
    String characterSelection();
    String enterUsername();
    String selectProfilePicture();
    String noProfilePictureSelected();
    String backToLobby();
    String confirm();

    // Game options
    String gameOptions();
    String roomSettings();
    String uniqueProfilePictures();
    String maximumNumberOfPlayers();
    String gameSettings();
    String cancel();
    String continueButton();

    // Static error / confirm messages
    String errRoomNameEmpty();
    String errRoomNameTooShort();
    String errRoomNameTooLong();
    String errSelectGame();
    String errRoomNameExists();
    String errNotAuthorised();
    String errPleaseEnterUsername();
    String errSelectProfilePicture();
    String errProfileTaken();
    String confirmDeleteRoom();
    String confirmLeavePlayingRoom();
}