package ADG.Lobby;

import ADG.Utils.LanguageSelectorWidget;
import ADG.i18n.I18n;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;

public class CharacterSelectionView extends Composite {

    interface Binder extends UiBinder<Widget, CharacterSelectionView> {}
    private static Binder uiBinder = GWT.create(Binder.class);

    @UiField VerticalPanel mainPanel;
    @UiField HTML pageTitle;
    @UiField FlowPanel langSelectorRow;
    @UiField HTML enterUsernameLabel;
    @UiField HTML selectProfileLabel;
    @UiField TextBox usernameInput;
    @UiField FlowPanel profilePicGrid;
    @UiField Label selectedProfileLabel;
    @UiField Button confirmButton;
    @UiField Button cancelButton;

    public CharacterSelectionView() {
        initWidget(uiBinder.createAndBindUi(this));
        langSelectorRow.add(new LanguageSelectorWidget());
        usernameInput.setMaxLength(10);
        pageTitle.setHTML("<h1>" + I18n.c().characterSelection() + "</h1>");
        enterUsernameLabel.setHTML("<span class=\"step-label\">" + I18n.c().enterUsername() + "</span>");
        selectProfileLabel.setHTML("<span class=\"step-label\">" + I18n.c().selectProfilePicture() + "</span>");
        selectedProfileLabel.setText(I18n.c().noProfilePictureSelected());
        cancelButton.setText(I18n.c().backToLobby());
        confirmButton.setText(I18n.c().confirm());
    }

    public TextBox getUsernameInput() { return usernameInput; }
    public FlowPanel getProfilePicGrid() { return profilePicGrid; }
    public Label getSelectedProfileLabel() { return selectedProfileLabel; }
    public Button getConfirmButton() { return confirmButton; }
    public Button getCancelButton() { return cancelButton; }

    public void showAlert(String message) {
        Window.alert(message);
    }
}