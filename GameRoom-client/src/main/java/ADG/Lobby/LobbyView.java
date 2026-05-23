package ADG.Lobby;

import ADG.Utils.Cookie;
import ADG.Utils.LanguageSelectorWidget;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;

import java.util.ArrayList;
import java.util.List;

public class LobbyView extends Composite {

    interface GameLobbyViewUiBinder extends UiBinder<Widget, LobbyView> {}
    private final static GameLobbyViewUiBinder uiBinder = GWT.create(GameLobbyViewUiBinder.class);

    @UiField VerticalPanel mainPanel;
    @UiField FlowPanel langSelectorRow;
    @UiField FlowPanel createRoomPanel;
    @UiField HTML lobbyTitle;
    @UiField HTML createRoomTitle;
    @UiField Label roomNameLabel;
    @UiField Label selectGameLabel;
    @UiField TextBox roomNameInput;
    @UiField Button createRoomButton;
    @UiField Button randomNameButton;
    @UiField Label availableRoomsHeader;
    @UiField ListBox gameListBox;
    @UiField FlowPanel roomTableContainer;

    public interface JoinHandler {
        void onJoin(Room room);
    }

    public interface DeleteHandler {
        void onDelete(Room room);
    }

    private JoinHandler joinHandler;
    private DeleteHandler deleteHandler;
    private String currentPlayerId;
    private boolean adminMode = false;
    private ArrayList<GameDefinition> gameDefinitions = new ArrayList<>();

    public LobbyView() {
        initWidget(uiBinder.createAndBindUi(this));
        langSelectorRow.add(new LanguageSelectorWidget());
        lobbyTitle.setHTML("<h1>" + I18n.c().gameLobby() + "</h1>");
        createRoomTitle.setHTML("<h2 class=\"section-title\">" + I18n.c().createARoom() + "</h2>");
        roomNameLabel.setText(I18n.c().roomName());
        selectGameLabel.setText(I18n.c().selectGame());
        createRoomButton.setText(I18n.c().createRoom());
        randomNameButton.setText("🎲 " + I18n.c().randomName());
        availableRoomsHeader.setText(I18n.c().availableRooms());
        buildTableHeader();
    }

    public Button getCreateRoomButton() { return createRoomButton; }
    public Button getRandomNameButton() { return randomNameButton; }
    public TextBox getRoomNameInput() { return roomNameInput; }
    public HTML getCreateRoomTitle() { return createRoomTitle; }

    public void toggleCreateRoom() {
        if (createRoomPanel.getStyleName().contains("create-room-card--open")) {
            createRoomPanel.removeStyleName("create-room-card--open");
        } else {
            createRoomPanel.addStyleName("create-room-card--open");
        }
    }

    public void setJoinHandler(JoinHandler handler) {
        this.joinHandler = handler;
    }

    public void setDeleteHandler(DeleteHandler handler) {
        this.deleteHandler = handler;
    }

    public void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }

    public void setCurrentPlayerId(String playerId) {
        this.currentPlayerId = playerId;
    }

    public void populateGameList(ArrayList<GameDefinition> games) {
        gameDefinitions = games;
        gameListBox.clear();
        for (GameDefinition game : games) {
            gameListBox.addItem(game.getName(), game.getId());
        }
    }

    private String getGameName(String gameId) {
        for (GameDefinition def : gameDefinitions) {
            if (def.getId().equals(gameId)) return def.getName();
        }
        return gameId;
    }

    public String getSelectedGameId() {
        int index = gameListBox.getSelectedIndex();
        return index >= 0 ? gameListBox.getValue(index) : null;
    }

    public void addGameSelectionChangeHandler(ChangeHandler handler) {
        gameListBox.addChangeHandler(handler);
    }

    public void showAlert(String msg) {
        Window.alert(msg);
    }

    public void updateRoomTable(List<Room> rooms) {
        // Keep header row (index 0), remove the rest
        while (roomTableContainer.getWidgetCount() > 1) {
            roomTableContainer.remove(1);
        }
        for (Room room : rooms) {
            roomTableContainer.add(buildRow(room));
        }
    }

    private void buildTableHeader() {
        FlowPanel header = new FlowPanel();
        header.setStyleName("room-table-header");
        header.add(makeHeaderCell(I18n.c().colRoomName(), "room-cell-name"));
        header.add(makeHeaderCell(I18n.c().colGame(),     "room-cell-game"));
        header.add(makeHeaderCell(I18n.c().colPlayers(),  "room-cell-players"));
        header.add(makeHeaderCell(I18n.c().colStatus(),   "room-cell-status"));
        header.add(makeHeaderCell("",                     "room-cell-action room-cell-button-header"));
        roomTableContainer.add(header);
    }

    private FlowPanel buildRow(Room room) {
        FlowPanel row = new FlowPanel();
        row.setStyleName("room-table-row");

        Label nameLabel = new Label(room.getName());
        nameLabel.setStyleName("room-table-cell room-cell-name");
        row.add(nameLabel);

        Label gameLabel = new Label(getGameName(room.getGameId()));
        gameLabel.setStyleName("room-table-cell room-cell-game");
        row.add(gameLabel);

        Label playersLabel = new Label(room.getNrOfPlayers() + " / " + room.getMaxPlayers());
        playersLabel.setStyleName("room-table-cell room-cell-players");
        row.add(playersLabel);

        Label statusLabel = new Label(getStatusText(room.getStatus()));
        statusLabel.setStyleName("room-table-cell room-cell-status " + getStatusClass(room.getStatus()));
        row.add(statusLabel);

        FlowPanel actionCell = new FlowPanel();
        actionCell.setStyleName("room-table-cell room-cell-action");
        boolean isMember = currentPlayerId != null && room.getPlayerIds().contains(currentPlayerId);
        if (isMember) {
            Button rejoinBtn = new Button(I18n.c().rejoin());
            rejoinBtn.setStylePrimaryName("joinRoomButton");
            rejoinBtn.addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); if (joinHandler != null) joinHandler.onJoin(room); });
            actionCell.add(rejoinBtn);
        } else if (GameStatus.WAITING.equals(room.getStatus())) {
            Button joinBtn = new Button(I18n.c().join());
            joinBtn.setStylePrimaryName("joinRoomButton");
            joinBtn.addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); if (joinHandler != null) joinHandler.onJoin(room); });
            actionCell.add(joinBtn);
        }
        if (adminMode) {
            Button deleteBtn = new Button("✕");
            deleteBtn.setStylePrimaryName("adminDeleteButton");
            deleteBtn.addClickHandler(e -> { if (deleteHandler != null) deleteHandler.onDelete(room); });
            actionCell.add(deleteBtn);
        }
        row.add(actionCell);

        return row;
    }

    private Label makeHeaderCell(String text, String extraStyle) {
        Label label = new Label(text);
        label.setStyleName("room-table-header-cell " + extraStyle);
        return label;
    }

    private String getStatusText(GameStatus status) {
        switch (status) {
            case PLAYING: return I18n.c().statusPlaying();
            case WAITING: return I18n.c().statusWaiting();
            case FULL:    return I18n.c().statusFull();
            default:      return "";
        }
    }

    private String getStatusClass(GameStatus status) {
        switch (status) {
            case PLAYING: return "status-playing";
            case WAITING: return "status-waiting";
            case FULL:    return "status-full";
            default:      return "";
        }
    }
}