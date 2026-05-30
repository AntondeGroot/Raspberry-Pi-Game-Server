package ADG.Lobby;

import ADG.Utils.GameTranslations;
import ADG.Utils.LanguageSelectorWidget;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GameOptionsView extends Composite {

    interface Binder extends UiBinder<Widget, GameOptionsView> {}
    private static Binder uiBinder = GWT.create(Binder.class);

    @UiField HTML      pageTitle;
    @UiField FlowPanel langSelectorRow;
    @UiField HTML      roomSettingsTitle;
    @UiField HTML      gameSettingsTitle;
    @UiField FlowPanel gameSpecificOptionsPanel;
    @UiField CheckBox  uniqueProfilePicsCheckbox;
    @UiField FlowPanel maxPlayersField;
    @UiField Label     maxPlayersLabel;
    @UiField TextBox   maxPlayersInput;
    @UiField Label     maxPlayersRangeHint;
    @UiField Button    confirmButton;
    @UiField Button    cancelButton;

    private int minBound;
    private int maxBound;
    private final ArrayList<String> gameOptionKeys = new ArrayList<>();
    private final ArrayList<Widget> gameOptionWidgets = new ArrayList<>();

    public GameOptionsView() {
        initWidget(uiBinder.createAndBindUi(this));
        langSelectorRow.add(new LanguageSelectorWidget());
        pageTitle.setHTML("<h1>" + I18n.c().gameOptions() + "</h1>");
        roomSettingsTitle.setHTML("<h2 class=\"section-title\">" + I18n.c().roomSettings() + "</h2>");
        gameSettingsTitle.setHTML("<h2 class=\"section-title\">" + I18n.c().gameSettings() + "</h2>");
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

    public void showGameSpecificOptions(ArrayList<GameOption> options, HashMap<String, String> currentValues) {
        gameOptionKeys.clear();
        gameOptionWidgets.clear();
        // Remove all children after the heading (index 0)
        while (gameSpecificOptionsPanel.getWidgetCount() > 1) {
            gameSpecificOptionsPanel.remove(1);
        }
        if (options == null || options.isEmpty()) {
            gameSpecificOptionsPanel.setVisible(false);
            return;
        }
        for (GameOption option : options) {
            // Prefer the room's stored value; fall back to the game-defined default.
            String currentValue = (currentValues != null && currentValues.containsKey(option.getKey()))
                    ? currentValues.get(option.getKey())
                    : option.getDefaultValue();
            FlowPanel row = new FlowPanel();
            row.addStyleName("game-options-field-inline");
            Widget inputWidget;
            if ("BOOLEAN".equals(option.getType())) {
                CheckBox cb = new CheckBox(GameTranslations.translate(option.getLabelKey()));
                cb.addStyleName("game-options-checkbox");
                cb.setValue("true".equalsIgnoreCase(currentValue));
                inputWidget = cb;
            } else if (option.getChoices() != null && !option.getChoices().isEmpty()) {
                Label lbl = new Label(GameTranslations.translate(option.getLabelKey()));
                lbl.addStyleName("game-options-label");
                ListBox lb = new ListBox();
                lb.addStyleName("game-options-select");
                for (String choice : option.getChoices()) {
                    String translatedChoice = GameTranslations.translate("gameOption.choice." + choice);
                    lb.addItem(translatedChoice, choice);
                }
                String selectValue = currentValue != null ? currentValue : option.getDefaultValue();
                if (selectValue != null) {
                    for (int i = 0; i < lb.getItemCount(); i++) {
                        if (lb.getValue(i).equals(selectValue)) {
                            lb.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                row.add(lbl);
                inputWidget = lb;
            } else {
                Label lbl = new Label(GameTranslations.translate(option.getLabelKey()));
                lbl.addStyleName("game-options-label");
                TextBox tb = new TextBox();
                tb.addStyleName("game-options-number-input");
                tb.setText(currentValue != null ? currentValue : "");
                row.add(lbl);
                inputWidget = tb;
            }
            row.add(inputWidget);
            if (option.getDescriptionKey() != null && !option.getDescriptionKey().isEmpty()) {
                Label desc = new Label(GameTranslations.translate(option.getDescriptionKey()));
                desc.addStyleName("game-options-description");
                row.add(desc);
            }
            gameSpecificOptionsPanel.add(row);
            gameOptionKeys.add(option.getKey());
            gameOptionWidgets.add(inputWidget);
        }
        gameSpecificOptionsPanel.setVisible(true);
        applyMutualExclusions(options);
    }

    private void applyMutualExclusions(ArrayList<GameOption> options) {
        HashSet<String> wiredPairs = new HashSet<>();
        for (GameOption option : options) {
            ArrayList<String> incompatibles = option.getIncompatibleWith();
            if (incompatibles == null || incompatibles.isEmpty()) continue;
            for (String otherKey : incompatibles) {
                String pairKey = option.getKey().compareTo(otherKey) < 0
                        ? option.getKey() + "|" + otherKey
                        : otherKey + "|" + option.getKey();
                if (wiredPairs.add(pairKey)) {
                    wireMutualExclusion(option.getKey(), otherKey);
                }
            }
        }
    }

    private void wireMutualExclusion(String keyA, String keyB) {
        int idxA = gameOptionKeys.indexOf(keyA);
        int idxB = gameOptionKeys.indexOf(keyB);
        if (idxA < 0 || idxB < 0) return;
        Widget wA = gameOptionWidgets.get(idxA);
        Widget wB = gameOptionWidgets.get(idxB);
        if (!(wA instanceof CheckBox) || !(wB instanceof CheckBox)) return;
        CheckBox cbA = (CheckBox) wA;
        CheckBox cbB = (CheckBox) wB;

        // Apply initial state before wiring handlers
        if (cbA.getValue())      setOptionDisabled(idxB, true);
        else if (cbB.getValue()) setOptionDisabled(idxA, true);

        cbA.addValueChangeHandler(e -> setOptionDisabled(idxB, e.getValue()));
        cbB.addValueChangeHandler(e -> setOptionDisabled(idxA, e.getValue()));
    }

    private void setOptionDisabled(int idx, boolean disabled) {
        Widget w = gameOptionWidgets.get(idx);
        if (w instanceof CheckBox cb) {
            if (disabled) cb.setValue(false);
            cb.setEnabled(!disabled);
        }
        // Row index is offset by 1 because index 0 is the section heading.
        Widget row = gameSpecificOptionsPanel.getWidget(idx + 1);
        if (disabled) row.addStyleName("option-disabled");
        else          row.removeStyleName("option-disabled");
    }

    public HashMap<String, String> collectGameOptions() {
        if (gameOptionKeys.isEmpty()) return null;
        HashMap<String, String> result = new HashMap<>();
        for (int i = 0; i < gameOptionKeys.size(); i++) {
            Widget w = gameOptionWidgets.get(i);
            String value;
            if (w instanceof CheckBox) {
                value = String.valueOf(((CheckBox) w).getValue());
            } else if (w instanceof ListBox) {
                ListBox lb = (ListBox) w;
                value = lb.getValue(lb.getSelectedIndex());
            } else {
                value = ((TextBox) w).getText().trim();
            }
            result.put(gameOptionKeys.get(i), value);
        }
        return result;
    }
}