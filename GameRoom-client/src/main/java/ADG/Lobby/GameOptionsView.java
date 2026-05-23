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

    public void showGameSpecificOptions(ArrayList<GameOption> options) {
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
            FlowPanel row = new FlowPanel();
            row.addStyleName("game-options-field-inline");
            Widget inputWidget;
            if ("BOOLEAN".equals(option.getType())) {
                CheckBox cb = new CheckBox(GameTranslations.translate(option.getLabelKey()));
                cb.addStyleName("game-options-checkbox");
                cb.setValue("true".equalsIgnoreCase(option.getDefaultValue()));
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
                if (option.getDefaultValue() != null) {
                    for (int i = 0; i < lb.getItemCount(); i++) {
                        if (lb.getValue(i).equals(option.getDefaultValue())) {
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
                tb.setText(option.getDefaultValue() != null ? option.getDefaultValue() : "");
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