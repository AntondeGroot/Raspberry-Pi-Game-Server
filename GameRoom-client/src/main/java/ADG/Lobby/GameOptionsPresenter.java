package ADG.Lobby;

import ADG.Presenter;
import ADG.PresenterManager;
import ADG.Utils.Cookie;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.HashMap;

public class GameOptionsPresenter implements Presenter {

    private final GameOptionsView view;
    private final Room room;
    private final PresenterManager presenterManager;
    private final RoomServiceAsync roomService;
    private HandlerRegistration confirmReg;
    private HandlerRegistration cancelReg;
    private boolean stopped = false;

    public GameOptionsPresenter(GameOptionsView view, Room room, PresenterManager presenterManager, RoomServiceAsync roomService) {
        this.view = view;
        this.room = room;
        this.presenterManager = presenterManager;
        this.roomService = roomService;
    }

    @Override
    public void start() {
        view.init(room);
        confirmReg = view.getConfirmButton().addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); onConfirm(); });
        cancelReg  = view.getCancelButton().addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); onCancel(); });

        // Embed the game's own settings UI in an iframe so it can render its
        // native visuals (option chips, sheet preview, etc.).
        // The iframe pushes option changes back to this page via postMessage.
        String baseUrl = resolvePublicBaseUrl();
        if (baseUrl != null) {
            view.showGameSettingsFrame(baseUrl, Cookie.getLanguage().name().toLowerCase(),
                    room.getGameOptions());
        }
    }

    /** Returns the public base URL for the game, or null if none is available. */
    private String resolvePublicBaseUrl() {
        String base = room.getGameBaseUrl();
        if (base != null && !base.isEmpty() && !base.contains("localhost")) {
            return base;
        }
        String gameId = room.getGameId();
        if (gameId != null && !gameId.isEmpty()) {
            // Derive the URL from the current page's origin
            String protocol = Window.Location.getProtocol();
            String host = Window.Location.getHost();
            return protocol + "//" + host + "/" + gameId;
        }
        return null;
    }

    @Override
    public void stop() {
        stopped = true;
        view.tearDownMessageListener();
        if (confirmReg != null) { confirmReg.removeHandler(); confirmReg = null; }
        if (cancelReg  != null) { cancelReg.removeHandler();  cancelReg  = null; }
    }

    private void onConfirm() {
        int maxPlayers = view.getMaxPlayers();
        if (maxPlayers < view.getMinBound() || maxPlayers > view.getMaxBound()) {
            Window.alert(I18n.m().errMaxPlayersBetween(view.getMinBound(), view.getMaxBound()));
            return;
        }
        room.setMaxPlayers(maxPlayers);
        room.setUniqueProfilePics(view.isUniqueProfilePics());

        // Use the options received from the game's own settings iframe.
        // Fall back to whatever was already stored on the room if the iframe
        // hasn't posted anything yet (e.g., user opened and immediately confirmed).
        HashMap<String, String> gameOpts = view.getIframeOptions();
        if (gameOpts != null) {
            room.setGameOptions(gameOpts);
        }

        GWT.log("Updating room with ID: " + room.getId());
        roomService.updateRoom(room, new AsyncCallback<Void>() {
            @Override public void onFailure(Throwable t) {
                if (stopped) return;
                String errorMsg = "Unknown error";
                if (t instanceof ADG.Lobby.RoomServiceException) {
                    errorMsg = t.getMessage();
                } else if (t.getMessage() != null) {
                    errorMsg = t.getMessage();
                }
                GWT.log("Failed to update room: " + errorMsg);
                Window.alert("Failed to save game options: " + errorMsg);
            }
            @Override public void onSuccess(Void v) {
                GWT.log("Room updated successfully, switching to room");
                presenterManager.switchToGameRoom(room);
            }
        });
    }

    private void onCancel() {
        presenterManager.switchToGameRoom(room);
    }
}
