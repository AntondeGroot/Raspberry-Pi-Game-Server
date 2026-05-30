package ADG.Lobby;

import ADG.Utils.Cookie;
import ADG.Utils.LanguageSelectorWidget;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
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
    @UiField TextBox roomNameInput;
    @UiField Button createRoomButton;
    @UiField Button randomNameButton;
    @UiField Label availableRoomsHeader;
    @UiField FlowPanel roomTableContainer;

    public interface JoinHandler {
        void onJoin(Room room);
    }

    public interface DeleteHandler {
        void onDelete(Room room);
    }

    public interface RemovePlayerHandler {
        void onRemovePlayer(Room room, String playerId, String playerName);
    }

    private Button adminLoginHintButton;

    private JoinHandler joinHandler;
    private DeleteHandler deleteHandler;
    private RemovePlayerHandler removePlayerHandler;
    private String currentPlayerId;
    private boolean adminMode = false;
    private ArrayList<GameDefinition> gameDefinitions = new ArrayList<>();

    public LobbyView() {
        initWidget(uiBinder.createAndBindUi(this));
        // Admin login hint button — sits on the left of the top bar; hidden until
        // the admin_hint cookie is detected (set on successful admin login).
        adminLoginHintButton = new Button("🔐 Login");
        adminLoginHintButton.setStylePrimaryName("adminLoginHintButton");
        adminLoginHintButton.setVisible(false);
        adminLoginHintButton.addClickHandler(e -> Window.Location.assign("/login"));
        langSelectorRow.add(adminLoginHintButton);
        langSelectorRow.add(new LanguageSelectorWidget());
        lobbyTitle.setHTML("<h1>" + I18n.c().gameLobby() + "</h1>");
        createRoomTitle.setHTML("<h2 class=\"section-title\">" + I18n.c().createARoom() + "</h2>");
        roomNameLabel.setText(I18n.c().roomName());
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

    public void setRemovePlayerHandler(RemovePlayerHandler handler) {
        this.removePlayerHandler = handler;
    }

    public void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }

    public void showAdminLoginHintButton() {
        adminLoginHintButton.setVisible(true);
    }

    public void setCurrentPlayerId(String playerId) {
        this.currentPlayerId = playerId;
    }

    public void populateGameList(ArrayList<GameDefinition> games) {
        gameDefinitions = games;
    }

    private String getGameName(String gameId) {
        for (GameDefinition def : gameDefinitions) {
            if (def.getId().equals(gameId)) return def.getName();
        }
        return gameId;
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

        // Name cell — admin gets a triangle toggle; everyone else gets a plain label
        if (adminMode) {
            FlowPanel nameCell = new FlowPanel();
            nameCell.setStyleName("room-table-cell room-cell-name admin-name-cell");
            FlowPanel dropdown = buildPlayerDropdown(room);
            Button triangle = new Button("▶");
            triangle.setStylePrimaryName("admin-triangle-btn");
            triangle.addClickHandler(e -> {
                boolean open = dropdown.isVisible();
                dropdown.setVisible(!open);
                triangle.setText(open ? "▶" : "▼");
            });
            InlineLabel nameText = new InlineLabel(room.getName());
            nameText.setStyleName("admin-room-name");
            nameCell.add(triangle);
            nameCell.add(nameText);
            row.add(nameCell);

            Label gameLabel = new Label(getGameName(room.getGameId()));
            gameLabel.setStyleName("room-table-cell room-cell-game");
            row.add(gameLabel);

            Label playersLabel = new Label(room.getNrOfPlayers() + " / " + room.getMaxPlayers());
            playersLabel.setStyleName("room-table-cell room-cell-players");
            row.add(playersLabel);

            Label statusLabel = new Label(getStatusText(room));
            statusLabel.setStyleName("room-table-cell room-cell-status " + getStatusClass(room));
            row.add(statusLabel);

            FlowPanel actionCell = buildActionCell(room);
            row.add(actionCell);

            // Dropdown spans the full row width; placed last so grid auto-places it in row 2
            row.add(dropdown);
        } else {
            Label nameLabel = new Label(room.getName());
            nameLabel.setStyleName("room-table-cell room-cell-name");
            row.add(nameLabel);

            Label gameLabel = new Label(getGameName(room.getGameId()));
            gameLabel.setStyleName("room-table-cell room-cell-game");
            row.add(gameLabel);

            Label playersLabel = new Label(room.getNrOfPlayers() + " / " + room.getMaxPlayers());
            playersLabel.setStyleName("room-table-cell room-cell-players");
            row.add(playersLabel);

            Label statusLabel = new Label(getStatusText(room));
            statusLabel.setStyleName("room-table-cell room-cell-status " + getStatusClass(room));
            row.add(statusLabel);

            row.add(buildActionCell(room));
        }

        return row;
    }

    private FlowPanel buildActionCell(Room room) {
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
        return actionCell;
    }

    private FlowPanel buildPlayerDropdown(Room room) {
        FlowPanel dropdown = new FlowPanel();
        dropdown.setStyleName("admin-player-dropdown");
        dropdown.setVisible(false);

        if (room.getPlayerIds().isEmpty()) {
            InlineLabel empty = new InlineLabel("No players in this room.");
            empty.setStyleName("admin-player-empty");
            dropdown.add(empty);
            return dropdown;
        }

        for (String playerId : room.getPlayerIds()) {
            String playerName = room.getPlayerNames().get(playerId);
            if (playerName == null) playerName = playerId;
            final String fId = playerId;
            final String fName = playerName;

            FlowPanel playerRow = new FlowPanel();
            playerRow.setStyleName("admin-player-row");

            InlineLabel nameLabel = new InlineLabel(playerName);
            nameLabel.setStyleName("admin-player-name");

            Button shareBtn = new Button("↗");
            shareBtn.setStylePrimaryName("admin-share-btn");
            shareBtn.setTitle("Copy rejoin link to clipboard");
            shareBtn.addClickHandler(e -> copyToClipboard("/rejoin?pid=" + fId + "&rid=" + room.getId()));

            Button removeBtn = new Button("✕");
            removeBtn.setStylePrimaryName("admin-remove-player-btn");
            removeBtn.addClickHandler(e -> {
                if (removePlayerHandler != null) removePlayerHandler.onRemovePlayer(room, fId, fName);
            });

            playerRow.add(nameLabel);
            playerRow.add(shareBtn);
            playerRow.add(removeBtn);
            dropdown.add(playerRow);
        }

        return dropdown;
    }

    public static native void copyToClipboard(String url) /*-{
        var full = $wnd.location.origin + url;
        if ($wnd.navigator.clipboard) {
            $wnd.navigator.clipboard.writeText(full)['catch'](function() {
                $wnd.prompt("Copy this rejoin link:", full);
            });
        } else {
            $wnd.prompt("Copy this rejoin link:", full);
        }
    }-*/;

    private Label makeHeaderCell(String text, String extraStyle) {
        Label label = new Label(text);
        label.setStyleName("room-table-header-cell " + extraStyle);
        return label;
    }

    private String getStatusText(Room room) {
        String base = getStatusText(room.getStatus());
        return room.hasPassword() ? "🔑 " + base : base;
    }

    private String getStatusText(GameStatus status) {
        switch (status) {
            case PLAYING: return I18n.c().statusPlaying();
            case WAITING: return I18n.c().statusWaiting();
            case FULL:    return I18n.c().statusFull();
            default:      return "";
        }
    }

    private String getStatusClass(Room room) {
        return getStatusClass(room.getStatus());
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