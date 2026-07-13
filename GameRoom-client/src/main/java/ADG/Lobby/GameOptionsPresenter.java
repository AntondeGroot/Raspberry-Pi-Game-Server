package ADG.Lobby;

import ADG.Presenter;
import ADG.PresenterManager;
import ADG.Utils.Cookie;
import ADG.Utils.Notify;
import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.ArrayList;
import java.util.HashMap;

public class GameOptionsPresenter implements Presenter {

    private final GameOptionsView view;
    private final Room room;
    private final PresenterManager presenterManager;
    private final RoomServiceAsync roomService;
    private final boolean isAdmin;
    private HandlerRegistration confirmReg;
    private HandlerRegistration cancelReg;
    private boolean stopped = false;

    public GameOptionsPresenter(GameOptionsView view, Room room, PresenterManager presenterManager, RoomServiceAsync roomService, boolean isAdmin) {
        this.view = view;
        this.room = room;
        this.presenterManager = presenterManager;
        this.roomService = roomService;
        this.isAdmin = isAdmin;
    }

    @Override
    public void start() {
        view.init(room);
        confirmReg = view.getConfirmButton().addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); onConfirm(); });
        cancelReg  = view.getCancelButton().addClickHandler(e -> { AudioPlayer.play(AudioPlayer.BUTTON_CLICK); onCancel(); });

        GWT.log("[GameOptions] gameId=" + room.getGameId()
                + " embedded=" + room.isEmbeddedSettings()
                + " baseUrl=" + room.getGameBaseUrl());

        if (room.isEmbeddedSettings()) {
            // The game server exposes /settings?embed=1 — embed it in an iframe.
            // The iframe posts option changes back to this page via postMessage.
            String baseUrl = resolvePublicBaseUrl();
            GWT.log("[GameOptions] resolvedUrl=" + baseUrl);
            if (baseUrl != null) {
                view.showGameSettingsFrame(baseUrl, Cookie.getLanguage().name().toLowerCase(),
                        room.getGameOptions(), isAdmin);
            } else {
                // URL could not be resolved — fall back to generic widgets.
                GWT.log("[GameOptions] WARNING: baseUrl null despite embeddedSettings=true, falling back to generic options");
                loadGenericOptions();
            }
        } else {
            // No embedded settings page: fetch the option definitions and render
            // them using generic GWT widgets (checkbox, number input, drop-down).
            loadGenericOptions();
        }
    }

    private void loadGenericOptions() {
        String gameId = room.getGameId();
        if (gameId == null || gameId.isEmpty()) return;
        roomService.getGameOptions(gameId, new AsyncCallback<ArrayList<GameOption>>() {
            @Override public void onFailure(Throwable t) {
                if (stopped) return;
                GWT.log("Failed to fetch game options: " + t.getMessage());
                // Proceed without options — user can still adjust room settings.
            }
            @Override public void onSuccess(ArrayList<GameOption> options) {
                if (stopped) return;
                view.showGameSpecificOptions(options, room.getGameOptions());
            }
        });
    }

    /**
     * Returns the browser-accessible base URL for the game's settings iframe.
     *
     * The gameBaseUrl is typically an internal address (e.g. localhost:4300) that
     * the browser cannot reach directly — and must not be used as-is on an HTTPS
     * page (mixed-content block). Instead, we always derive the public URL from
     * the current page's origin so the iframe goes through the same reverse proxy
     * that serves the main app (e.g. nginx routes /qwixx/ → localhost:4300).
     *
     * Exception: if gameBaseUrl is an already-public external URL (no "localhost"),
     * use it directly — that covers deployments where the game is on its own domain.
     */
    private String resolvePublicBaseUrl() {
        String base = room.getGameBaseUrl();
        if (base != null && !base.isEmpty() && !base.contains("localhost")) {
            // External URL — safe to use directly (same-protocol assumed).
            return base;
        }
        // Internal / localhost address: derive the public URL from the current
        // page's origin so the request goes through the reverse proxy.
        String gameId = room.getGameId();
        if (gameId != null && !gameId.isEmpty()) {
            String protocol = Window.Location.getProtocol();
            String host = Window.Location.getHost();
            return protocol + "//" + host + "/" + gameId;
        }
        return null;
    }

    @Override
    public void stop() {
        stopped = true;
        if (room.isEmbeddedSettings()) {
            view.tearDownMessageListener();
        }
        if (confirmReg != null) { confirmReg.removeHandler(); confirmReg = null; }
        if (cancelReg  != null) { cancelReg.removeHandler();  cancelReg  = null; }
    }

    private void onConfirm() {
        int maxPlayers = view.getMaxPlayers();
        if (maxPlayers < view.getMinBound() || maxPlayers > view.getMaxBound()) {
            Notify.error(I18n.m().errMaxPlayersBetween(view.getMinBound(), view.getMaxBound()));
            return;
        }
        room.setMaxPlayers(maxPlayers);
        room.setUniqueProfilePics(view.isUniqueProfilePics());

        // Collect game-specific options from whichever input mechanism is active.
        if (room.isEmbeddedSettings()) {
            // Options come from the iframe via postMessage. Fall back to the room's
            // existing options if the iframe hasn't posted anything yet.
            HashMap<String, String> gameOpts = view.getIframeOptions();
            if (gameOpts != null) {
                room.setGameOptions(gameOpts);
            }
        } else {
            // Options come from the generic GWT widgets rendered by showGameSpecificOptions.
            room.setGameOptions(view.collectGameOptions());
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
                Notify.error("Failed to save game options: " + errorMsg);
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
