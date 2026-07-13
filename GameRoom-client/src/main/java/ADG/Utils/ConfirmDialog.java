package ADG.Utils;

import ADG.i18n.I18n;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;

/**
 * Stylised, on-brand replacement for the native, blocking {@code Window.confirm}.
 * Because a modal can't return synchronously, the accepted action is supplied as
 * a callback that runs only when the user confirms.
 *
 * <pre>
 *   ConfirmDialog.danger(message, I18n.c().confirm(), () -> deleteRoom());
 *   ConfirmDialog.show(message, () -> proceed());
 * </pre>
 */
public class ConfirmDialog {

    private static final String QUESTION_ICON =
        "<svg class='confirm-modal-icon' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'>"
      + "<circle cx='12' cy='12' r='9' stroke='#8b5cf6' stroke-width='1.7'/>"
      + "<path d='M9.4 9.2a2.6 2.6 0 0 1 5 .9c0 1.7-2.5 2-2.5 3.6' stroke='#fb923c' stroke-width='1.7' stroke-linecap='round'/>"
      + "<circle cx='12' cy='17' r='1' fill='#fb923c'/></svg>";

    private static final String WARN_ICON =
        "<svg class='confirm-modal-icon' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'>"
      + "<path d='M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z' stroke='#fb7185' stroke-width='1.7' stroke-linejoin='round'/>"
      + "<path d='M12 9v4' stroke='#fb7185' stroke-width='1.7' stroke-linecap='round'/>"
      + "<circle cx='12' cy='16.5' r='1' fill='#fb7185'/></svg>";

    /** Neutral confirmation with the default "Confirm" / "Cancel" labels. */
    public static void show(String message, Runnable onConfirm) {
        new ConfirmDialog().open(message, false, I18n.c().confirm(), onConfirm);
    }

    /** Destructive confirmation — red confirm button — with a custom confirm label. */
    public static void danger(String message, String confirmLabel, Runnable onConfirm) {
        new ConfirmDialog().open(message, true, confirmLabel, onConfirm);
    }

    private final PopupPanel popup = new PopupPanel(false, true); // autoHide=false, modal=true
    private HandlerRegistration previewReg;

    private void open(String message, boolean danger, String confirmLabel, Runnable onConfirm) {
        popup.setStyleName("confirm-modal-popup");
        popup.setGlassEnabled(true);
        popup.setGlassStyleName("pw-modal-glass"); // shared glass backdrop
        popup.setAnimationEnabled(false);

        FlowPanel card = new FlowPanel();
        card.setStyleName("confirm-modal");
        card.add(new HTML(danger ? WARN_ICON : QUESTION_ICON));

        Label msg = new Label(message);
        msg.setStyleName("confirm-modal-message");
        card.add(msg);

        FlowPanel actions = new FlowPanel();
        actions.setStyleName("confirm-modal-actions");

        Button cancel = new Button(I18n.c().cancel());
        cancel.setStyleName("confirm-modal-btn confirm-modal-cancel");
        cancel.addClickHandler(e -> popup.hide());

        Button confirm = new Button(confirmLabel);
        confirm.setStyleName("confirm-modal-btn confirm-modal-confirm" + (danger ? " confirm-modal-confirm--danger" : ""));
        confirm.addClickHandler(e -> {
            popup.hide();
            if (onConfirm != null) onConfirm.run();
        });

        actions.add(cancel);
        actions.add(confirm);
        card.add(actions);

        popup.setWidget(card);

        // Escape cancels; remove the global handler once the popup closes.
        previewReg = Event.addNativePreviewHandler(ev -> {
            if (ev.getTypeInt() == Event.ONKEYDOWN
                    && ev.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
                popup.hide();
            }
        });
        popup.addCloseHandler(e -> {
            if (previewReg != null) {
                previewReg.removeHandler();
                previewReg = null;
            }
        });

        popup.center();
        confirm.setFocus(true);
    }
}