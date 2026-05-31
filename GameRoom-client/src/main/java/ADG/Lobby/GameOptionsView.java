package ADG.Lobby;

import ADG.Utils.LanguageSelectorWidget;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import java.util.HashMap;

public class GameOptionsView extends Composite {

    interface Binder extends UiBinder<Widget, GameOptionsView> {}
    private static Binder uiBinder = GWT.create(Binder.class);

    @UiField HTML      pageTitle;
    @UiField FlowPanel langSelectorRow;
    @UiField HTML      roomSettingsTitle;
    @UiField CheckBox  uniqueProfilePicsCheckbox;
    @UiField FlowPanel maxPlayersField;
    @UiField Label     maxPlayersLabel;
    @UiField TextBox   maxPlayersInput;
    @UiField Label     maxPlayersRangeHint;
    @UiField Frame     gameSettingsFrame;
    @UiField Button    confirmButton;
    @UiField Button    cancelButton;

    private int minBound;
    private int maxBound;

    /**
     * Last game-specific options received from the embedded game settings iframe
     * via postMessage. Null when no iframe is shown.
     */
    private HashMap<String, String> iframeOptions = null;

    public GameOptionsView() {
        initWidget(uiBinder.createAndBindUi(this));
        langSelectorRow.add(new LanguageSelectorWidget());
        pageTitle.setHTML("<h1>" + I18n.c().gameOptions() + "</h1>");
        roomSettingsTitle.setHTML("<h2 class=\"section-title\">" + I18n.c().roomSettings() + "</h2>");
        uniqueProfilePicsCheckbox.setText(I18n.c().uniqueProfilePictures());
        maxPlayersLabel.setText(I18n.c().maximumNumberOfPlayers());
        cancelButton.setText(I18n.c().cancel());
        confirmButton.setText(I18n.c().continueButton());
    }

    public void init(Room room) {
        uniqueProfilePicsCheckbox.setValue(room.isUniqueProfilePics());
        minBound = room.getMinPlayers();
        maxBound = room.getMaxPlayers();
        if (minBound == maxBound) {
            maxPlayersField.setVisible(false);
        } else {
            maxPlayersInput.setText(String.valueOf(maxBound));
            maxPlayersRangeHint.setText("(" + minBound + " – " + maxBound + ")");
        }
    }

    /**
     * Embed the game's own settings page in an iframe so it can show its
     * native visuals (option chips, sheet preview, etc.). The game will
     * postMessage the selected options back whenever they change.
     *
     * @param baseUrl   the game server's public base URL (e.g. "https://host/qwixx")
     * @param lang      the current UI language code (e.g. "en", "nl")
     * @param currentOptions  the options already stored on the room (may be empty)
     */
    public void showGameSettingsFrame(String baseUrl, String lang,
                                      HashMap<String, String> currentOptions) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            gameSettingsFrame.setVisible(false);
            return;
        }

        // Serialise current options as a JSON string so the iframe can pre-populate its form.
        String optionsParam = "";
        if (currentOptions != null && !currentOptions.isEmpty()) {
            JSONObject json = new JSONObject();
            for (String key : currentOptions.keySet()) {
                json.put(key, new JSONString(currentOptions.get(key)));
            }
            optionsParam = "&options=" + URL.encodeQueryString(json.toString());
        }

        String url = baseUrl + "/settings?embed=1&lang=" + URL.encodeQueryString(lang) + optionsParam;
        GWT.log("Embedding game settings frame: " + url);
        gameSettingsFrame.setUrl(url);
        gameSettingsFrame.setVisible(true);

        setupMessageListener();
    }

    /** Called from JSNI when the iframe posts a 'qwixx-options-changed' message. */
    void onIframeMessage(String optionsJson) {
        try {
            JSONValue val = JSONParser.parseStrict(optionsJson);
            JSONObject obj = val.isObject();
            if (obj == null) return;
            HashMap<String, String> opts = new HashMap<>();
            for (String key : obj.keySet()) {
                JSONValue v = obj.get(key);
                if (v.isString() != null) opts.put(key, v.isString().stringValue());
                else opts.put(key, v.toString());
            }
            iframeOptions = opts;
        } catch (Exception e) {
            GWT.log("Failed to parse iframe options: " + e.getMessage());
        }
    }

    /** Returns the last options received from the game settings iframe, or null if not available. */
    public HashMap<String, String> getIframeOptions() {
        return iframeOptions;
    }

    public boolean isUniqueProfilePics() { return uniqueProfilePicsCheckbox.getValue(); }

    /** Returns the entered value, or -1 if not a valid integer. */
    public int getMaxPlayers() {
        try {
            return Integer.parseInt(maxPlayersInput.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int getMinBound() { return minBound; }
    public int getMaxBound() { return maxBound; }

    public Button getConfirmButton() { return confirmButton; }
    public Button getCancelButton()  { return cancelButton; }

    // ── postMessage bridge ────────────────────────────────────────────────────

    private native void setupMessageListener() /*-{
        var self = this;
        var handler = function(event) {
            if (event.data && event.data.type === 'qwixx-options-changed'
                    && event.data.options) {
                var json = JSON.stringify(event.data.options);
                self.@ADG.Lobby.GameOptionsView::onIframeMessage(Ljava/lang/String;)(json);
            }
        };
        $wnd._gameOptionsMessageHandler = handler;
        $wnd.addEventListener('message', handler);
    }-*/;

    public native void tearDownMessageListener() /*-{
        if ($wnd._gameOptionsMessageHandler) {
            $wnd.removeEventListener('message', $wnd._gameOptionsMessageHandler);
            $wnd._gameOptionsMessageHandler = null;
        }
    }-*/;
}