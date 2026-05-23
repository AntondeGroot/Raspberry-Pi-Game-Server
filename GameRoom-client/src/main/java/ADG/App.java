package ADG;

import ADG.Lobby.RoomService;
import ADG.Lobby.RoomServiceAsync;
import ADG.Lobby.Room;
import ADG.Lobby.SpriteSheets;
import ADG.Utils.Cookie;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.Window;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class App implements EntryPoint {

	private final RoomServiceAsync roomService = GWT.create(RoomService.class);
	private final PresenterManager presenterManager = new PresenterManager();

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		Cookie.createPlayerIdCookie();
		Cookie.createLanguageCookie();
		if (Cookie.syncGwtLocale()) {
			return;
		}
		fetchSpriteSheetsConfig();
	}

	/**
	 * Fetches sprite-sheet config from the server, populates SpriteSheets,
	 * then proceeds with normal presenter routing.
	 * Falls back to an empty sheet list on error (no profile pics shown).
	 */
	private void fetchSpriteSheetsConfig() {
		try {
			RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, "/sprite-sheets");
			rb.setHeader("Accept", "application/json");
			rb.sendRequest(null, new RequestCallback() {
				@Override
				public void onResponseReceived(Request req, Response res) {
					if (res.getStatusCode() >= 200 && res.getStatusCode() < 300) {
						try {
							JSONArray arr = JSONParser.parseStrict(res.getText()).isArray();
							if (arr != null) {
								SpriteSheets.load(arr);
								preloadSpriteSheetImages();
							}
						} catch (Exception e) {
							GWT.log("sprite-sheets: JSON parse error: " + e.getMessage());
						}
					} else {
						GWT.log("sprite-sheets: unexpected status " + res.getStatusCode());
					}
					navigateToInitialPresenter();
				}

				@Override
				public void onError(Request req, Throwable ex) {
					GWT.log("sprite-sheets: fetch error: " + ex.getMessage());
					navigateToInitialPresenter();
				}
			});
		} catch (RequestException e) {
			GWT.log("sprite-sheets: request error: " + e.getMessage());
			navigateToInitialPresenter();
		}
	}

	/**
	 * Kicks off background downloads of all sprite-sheet images so the browser
	 * has them cached before the user reaches character selection.
	 * The hidden images are parked off-screen so GC doesn't discard them.
	 */
	private void preloadSpriteSheetImages() {
		for (SpriteSheets.Sheet sheet : SpriteSheets.all()) {
			Image img = new Image(sheet.url);
			img.getElement().getStyle().setPosition(Style.Position.ABSOLUTE);
			img.getElement().getStyle().setLeft(-9999, Style.Unit.PX);
			img.getElement().getStyle().setTop(-9999, Style.Unit.PX);
			RootPanel.get().add(img);
		}
	}

	private void navigateToInitialPresenter() {
		String token = History.getToken();
		if (token.startsWith("room=")) {
			String roomId = token.substring("room=".length());
			roomService.getRoomById(roomId, new AsyncCallback<Room>() {
				public void onFailure(Throwable caught) {
					GWT.log("failed to retrieve room");
					presenterManager.switchToLobby();
				}
				public void onSuccess(Room room) {
					if (room == null) { presenterManager.switchToLobby(); return; }
					presenterManager.switchToGameRoom(room);
				}
			});
		} else if (token.startsWith("settings=")) {
			// User was on Settings screen when language changed
			// Preserve that state by returning to Settings
			String roomId = token.substring("settings=".length());
			roomService.getRoomById(roomId, new AsyncCallback<Room>() {
				public void onFailure(Throwable caught) {
					GWT.log("failed to retrieve room for settings");
					presenterManager.switchToLobby();
				}
				public void onSuccess(Room room) {
					if (room == null) { presenterManager.switchToLobby(); return; }
					presenterManager.switchToGameOptions(room, presenterManager.getPreloadedGameOptions());
				}
			});
		} else if (token.startsWith("character=")) {
			// User was on CharacterSelection screen when language changed
			// Preserve that state by returning to CharacterSelection
			String roomId = token.substring("character=".length());
			roomService.getRoomById(roomId, new AsyncCallback<Room>() {
				public void onFailure(Throwable caught) {
					GWT.log("failed to retrieve room for character selection");
					presenterManager.switchToLobby();
				}
				public void onSuccess(Room room) {
					if (room != null && room.getPlayerIds().contains(Cookie.getPlayerId())) {
						presenterManager.switchToCharacterSelection(room);
					} else {
						presenterManager.switchToLobby();
					}
				}
			});
		} else if (token.startsWith("joining=")) {
			String roomId = token.substring("joining=".length());
			roomService.getRoomById(roomId, new AsyncCallback<Room>() {
				public void onFailure(Throwable caught) {
					presenterManager.switchToLobby();
				}
				public void onSuccess(Room room) {
					if (room != null && room.getPlayerIds().contains(Cookie.getPlayerId())) {
						presenterManager.switchToCharacterSelection(room);
					} else {
						presenterManager.switchToLobby();
					}
				}
			});
		} else {
			presenterManager.switchToLobby();
		}
	}
}