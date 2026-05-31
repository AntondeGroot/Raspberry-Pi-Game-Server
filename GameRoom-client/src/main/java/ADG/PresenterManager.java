package ADG;

import ADG.Lobby.*;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

public class PresenterManager {
    // Views
    private final LobbyView lobbyView = new LobbyView();
    private final RoomView roomView = new RoomView();
    private final CharacterSelectionView characterSelectionView = new CharacterSelectionView();
    private final GameOptionsView gameOptionsView = new GameOptionsView();
    // Presenters
    private LobbyPresenter lobbyPresenter;
    private RoomPresenter roomPresenter;
    private CharacterSelectionPresenter characterSelectionPresenter;
    private GameOptionsPresenter gameOptionsPresenter;
    private Presenter currentPresenter;
    // Services
    private final RoomServiceAsync roomServiceAsync = GWT.create(RoomService.class);
    private final MessageServiceAsync messageServiceAsync = GWT.create(MessageService.class);
    // State preservation for language changes
    private Room currentRoom;

    public void switchToGameRoom(Room room) {
        roomPresenter = new RoomPresenter(roomView, room, this, roomServiceAsync, messageServiceAsync);
        switchPresenter(roomPresenter, roomView);
    }

    public void switchToLobby() {
        if (lobbyPresenter == null) {
            lobbyPresenter = new LobbyPresenter(lobbyView, this, roomServiceAsync);
        }
        switchPresenter(lobbyPresenter, lobbyView);
    }

    public void switchToGameOptions(Room room) {
        currentRoom = room;
        History.newItem("settings=" + room.getId());
        gameOptionsPresenter = new GameOptionsPresenter(gameOptionsView, room, this, roomServiceAsync);
        switchPresenter(gameOptionsPresenter, gameOptionsView);
    }

    public void switchToCharacterSelection(Room room){
        currentRoom = room;
        History.newItem("character=" + room.getId());
        characterSelectionPresenter = new CharacterSelectionPresenter(characterSelectionView, room, this, roomServiceAsync);
        switchPresenter(characterSelectionPresenter, characterSelectionView);
    }

    // Getters for restoring state after language change
    public Room getCurrentRoom() {
        return currentRoom;
    }

    private void switchPresenter(Presenter newPresenter, Widget newView) {
        if (currentPresenter != null) {
            currentPresenter.stop();
        }
        RootPanel.get().clear();
        RootPanel.get().add(newView);
        currentPresenter = newPresenter;
        currentPresenter.start();
    }
}
