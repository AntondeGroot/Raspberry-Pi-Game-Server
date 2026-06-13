package ADG.Lobby;

import ADG.Utils.LanguageSelectorWidget;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
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
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    @UiField FlowPanel gameSpecificOptionsPanel;
    @UiField Frame     gameSettingsFrame;
    @UiField Button    confirmButton;
    @UiField Button    cancelButton;

    private int minBound;
    private int maxBound;

    /** Last options received from the embedded iframe via postMessage. Null when no iframe shown. */
    private HashMap<String, String> iframeOptions = null;

    // ── Generic (non-iframe) option widgets ───────────────────────────────────
    private ArrayList<GameOption> renderedOptions = new ArrayList<>();
    private final Map<String, CheckBox> booleanWidgets = new HashMap<>();
    private final Map<String, TextBox>  integerWidgets = new HashMap<>();
    private final Map<String, ListBox>  enumWidgets    = new HashMap<>();

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
                                      HashMap<String, String> currentOptions, boolean isAdmin) {
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

        setupMessageListener(isAdmin);
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

    // ── Generic option widgets (fallback for games without embedded settings) ──

    /**
     * Renders game-specific options as generic GWT widgets (checkbox for BOOLEAN,
     * text input for INTEGER, drop-down for ENUM). Pre-populates each widget with
     * the value in {@code currentValues} when present, falling back to the
     * option's declared {@code defaultValue}.
     */
    public void showGameSpecificOptions(ArrayList<GameOption> options,
                                        HashMap<String, String> currentValues) {
        gameSpecificOptionsPanel.clear();
        booleanWidgets.clear();
        integerWidgets.clear();
        enumWidgets.clear();
        renderedOptions = (options != null) ? options : new ArrayList<>();

        if (renderedOptions.isEmpty()) {
            gameSpecificOptionsPanel.setVisible(false);
            return;
        }

        for (GameOption option : renderedOptions) {
            if (option.isAdminOnly()) continue;
            String currentVal = (currentValues != null) ? currentValues.get(option.getKey()) : null;
            String effectiveVal = (currentVal != null) ? currentVal : option.getDefaultValue();
            String label = (option.getLabel() != null && !option.getLabel().isEmpty())
                    ? option.getLabel() : option.getKey();
            String type = option.getType();

            if ("BOOLEAN".equals(type)) {
                CheckBox cb = new CheckBox(label);
                cb.addStyleName("game-options-checkbox");
                cb.setValue(Boolean.parseBoolean(effectiveVal));
                booleanWidgets.put(option.getKey(), cb);
                gameSpecificOptionsPanel.add(cb);

            } else if ("INTEGER".equals(type)) {
                FlowPanel row = new FlowPanel();
                row.addStyleName("game-options-field-inline");
                Label lbl = new Label(label);
                lbl.addStyleName("game-options-label");
                TextBox tb = new TextBox();
                tb.addStyleName("game-options-number-input");
                tb.setText(effectiveVal != null ? effectiveVal : "");
                integerWidgets.put(option.getKey(), tb);
                row.add(lbl);
                row.add(tb);
                if (option.getMinValue() != null && option.getMaxValue() != null) {
                    Label hint = new Label("(" + option.getMinValue() + " – " + option.getMaxValue() + ")");
                    hint.addStyleName("game-options-range-hint");
                    row.add(hint);
                }
                gameSpecificOptionsPanel.add(row);

            } else if ("ENUM".equals(type)) {
                FlowPanel row = new FlowPanel();
                row.addStyleName("game-options-field-inline");
                Label lbl = new Label(label);
                lbl.addStyleName("game-options-label");
                ListBox lb = new ListBox();
                lb.addStyleName("game-options-select");
                if (option.getChoices() != null) {
                    int selectedIdx = 0;
                    for (int i = 0; i < option.getChoices().size(); i++) {
                        String choice = option.getChoices().get(i);
                        lb.addItem(choice, choice);
                        if (choice.equals(effectiveVal)) selectedIdx = i;
                    }
                    lb.setSelectedIndex(selectedIdx);
                }
                enumWidgets.put(option.getKey(), lb);
                row.add(lbl);
                row.add(lb);
                gameSpecificOptionsPanel.add(row);
            }
        }

        // Wire up mutual-exclusion: when a BOOLEAN option is enabled, disable
        // any incompatible BOOLEAN options so only one can be active at a time.
        for (final GameOption option : renderedOptions) {
            if (option.getIncompatibleWith() == null) continue;
            final CheckBox cb = booleanWidgets.get(option.getKey());
            if (cb == null) continue;
            final ArrayList<String> incompatibles = option.getIncompatibleWith();
            cb.addValueChangeHandler(event -> {
                if (Boolean.TRUE.equals(event.getValue())) {
                    for (String otherKey : incompatibles) {
                        CheckBox other = booleanWidgets.get(otherKey);
                        if (other != null) other.setValue(false);
                    }
                }
            });
        }

        gameSpecificOptionsPanel.setVisible(true);
    }

    /**
     * Reads the current values out of the generic option widgets and returns
     * them as a {@code key → stringified-value} map suitable for storing on the room.
     */
    public HashMap<String, String> collectGameOptions() {
        HashMap<String, String> result = new HashMap<>();
        for (GameOption option : renderedOptions) {
            if (option.isAdminOnly()) continue;
            String type = option.getType();
            if ("BOOLEAN".equals(type)) {
                CheckBox cb = booleanWidgets.get(option.getKey());
                if (cb != null) result.put(option.getKey(), String.valueOf(cb.getValue()));
            } else if ("INTEGER".equals(type)) {
                TextBox tb = integerWidgets.get(option.getKey());
                if (tb != null) result.put(option.getKey(), tb.getText().trim());
            } else if ("ENUM".equals(type)) {
                ListBox lb = enumWidgets.get(option.getKey());
                if (lb != null && lb.getSelectedIndex() >= 0) {
                    result.put(option.getKey(), lb.getValue(lb.getSelectedIndex()));
                }
            }
        }
        return result;
    }

    // ── postMessage bridge ────────────────────────────────────────────────────

    private native void setupMessageListener(boolean isAdmin) /*-{
        var self = this;
        var frame = this.@ADG.Lobby.GameOptionsView::gameSettingsFrame;
        var frameEl = frame.@com.google.gwt.user.client.ui.UIObject::getElement()();
        var handler = function(event) {
            if (event.data && event.data.type === 'qwixx-options-changed'
                    && event.data.options) {
                var json = JSON.stringify(event.data.options);
                self.@ADG.Lobby.GameOptionsView::onIframeMessage(Ljava/lang/String;)(json);
            }
        };
        $wnd._gameOptionsMessageHandler = handler;
        $wnd.addEventListener('message', handler);
        frameEl.addEventListener('load', function() {
            frameEl.contentWindow.postMessage({ type: 'qwixx-admin-status', isAdmin: isAdmin }, '*');
        });
    }-*/;

    public native void tearDownMessageListener() /*-{
        if ($wnd._gameOptionsMessageHandler) {
            $wnd.removeEventListener('message', $wnd._gameOptionsMessageHandler);
            $wnd._gameOptionsMessageHandler = null;
        }
    }-*/;
}