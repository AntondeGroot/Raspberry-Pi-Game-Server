package ADG.Utils;

import ADG.audio.AudioPlayer;
import ADG.i18n.I18n;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.PopupPanel;

/**
 * Stylised, on-brand replacement for the native {@code Window.prompt} used when
 * joining a password-protected room. Shows a glassy modal with a masked input;
 * a wrong password shakes the card and shows an inline error without closing.
 *
 * Usage:
 * <pre>
 *   new PasswordPromptDialog().show(
 *       room.getName(),
 *       pw -> pw.equalsIgnoreCase(room.getRoomPassword()),
 *       () -> navigateToCharacterSelection(room));
 * </pre>
 */
public class PasswordPromptDialog {

    /** Returns {@code true} when the entered password is accepted. */
    public interface Verifier {
        boolean verify(String password);
    }

    private final PopupPanel popup = new PopupPanel(false, true); // autoHide=false, modal=true
    private final FlowPanel card = new FlowPanel();
    private final Label roomLabel = new Label();
    private final PasswordTextBox input = new PasswordTextBox();
    private final Label error = new Label();

    private Verifier verifier;
    private Runnable onSuccess;

    public PasswordPromptDialog() {
        popup.setStyleName("pw-modal-popup");
        popup.setGlassEnabled(true);
        popup.setGlassStyleName("pw-modal-glass");
        popup.setAnimationEnabled(false); // we animate the card ourselves via CSS

        card.setStyleName("pw-modal");

        HTML icon = new HTML(
            "<svg class=\"pw-modal-icon\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">"
          + "<rect x=\"4\" y=\"10.5\" width=\"16\" height=\"10\" rx=\"2.5\" stroke=\"#fb923c\" stroke-width=\"1.7\"/>"
          + "<path d=\"M7.5 10.5V7.5a4.5 4.5 0 0 1 9 0v3\" stroke=\"#8b5cf6\" stroke-width=\"1.7\" stroke-linecap=\"round\"/>"
          + "<circle cx=\"12\" cy=\"15\" r=\"1.6\" fill=\"#fb923c\"/>"
          + "<path d=\"M12 16.5v1.7\" stroke=\"#fb923c\" stroke-width=\"1.7\" stroke-linecap=\"round\"/>"
          + "</svg>");
        card.add(icon);

        Label title = new Label(I18n.c().enterPasswordPrompt());
        title.setStyleName("pw-modal-title");
        card.add(title);

        roomLabel.setStyleName("pw-modal-room");
        card.add(roomLabel);

        input.setStyleName("pw-modal-input");
        // Keep the browser's password manager away — this is a room password, not
        // a login, and we don't want saved admin credentials autofilled here.
        input.getElement().setAttribute("autocomplete", "new-password");
        input.getElement().setAttribute("name", "gameroom-room-password");
        input.getElement().setAttribute("autocorrect", "off");
        input.getElement().setAttribute("autocapitalize", "none");
        input.getElement().setAttribute("spellcheck", "false");
        input.getElement().setAttribute("data-lpignore", "true");
        input.getElement().setAttribute("data-1p-ignore", "true");
        card.add(input);

        error.setStyleName("pw-modal-error");
        card.add(error);

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("pw-modal-actions");
        Button cancel = new Button(I18n.c().cancel());
        cancel.setStyleName("pw-modal-btn pw-modal-cancel");
        cancel.addClickHandler(e -> cancel());
        Button submit = new Button(I18n.c().join());
        submit.setStyleName("pw-modal-btn pw-modal-submit");
        submit.addClickHandler(e -> submit());
        actions.add(cancel);
        actions.add(submit);
        card.add(actions);

        popup.setWidget(card);

        input.addKeyDownHandler(e -> {
            int key = e.getNativeKeyCode();
            if (key == KeyCodes.KEY_ENTER) {
                e.preventDefault();
                submit();
            } else if (key == KeyCodes.KEY_ESCAPE) {
                cancel();
            } else {
                clearError(); // typing dismisses the previous error
            }
        });
    }

    /**
     * Shows the dialog for {@code roomName}. {@code prefill} pre-populates the
     * field (e.g. a password this browser used before — may be {@code null}).
     * {@code verifier} decides whether the typed password is correct; on success
     * the dialog closes and {@code onSuccess} runs, on failure it shakes and
     * shows an inline error.
     */
    public void show(String roomName, String prefill, Verifier verifier, Runnable onSuccess) {
        this.verifier = verifier;
        this.onSuccess = onSuccess;
        roomLabel.setText(roomName);
        clearError();
        input.setText(prefill != null ? prefill : "");
        popup.center(); // centres and shows
        input.setFocus(true);
        input.selectAll(); // so a prefilled value can be overtyped in one go
    }

    private void submit() {
        String password = input.getText();
        if (verifier != null && verifier.verify(password)) {
            popup.hide();
            if (onSuccess != null) onSuccess.run();
            return;
        }
        AudioPlayer.play(AudioPlayer.ERROR);
        error.setText(I18n.c().wrongPassword());
        error.addStyleName("pw-modal-error--visible");
        input.addStyleName("pw-modal-input--error");
        input.selectAll();
        input.setFocus(true);
        // Restart the shake animation: drop the class, force a reflow, re-add it.
        card.removeStyleName("pw-modal--shake");
        card.getOffsetWidth();
        card.addStyleName("pw-modal--shake");
    }

    private void clearError() {
        error.removeStyleName("pw-modal-error--visible");
        input.removeStyleName("pw-modal-input--error");
    }

    private void cancel() {
        popup.hide();
    }
}