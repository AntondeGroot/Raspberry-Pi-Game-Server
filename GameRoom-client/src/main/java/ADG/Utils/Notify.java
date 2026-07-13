package ADG.Utils;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * Lightweight, on-brand toast notifications — the styled replacement for the
 * native {@code Window.alert}. Toasts stack top-centre, auto-dismiss, and can be
 * clicked away. Non-blocking, so callers continue immediately (the old alerts
 * never depended on a return value).
 */
public final class Notify {

    private static final int VISIBLE_MS = 4500;

    private static final String ERROR_ICON =
        "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' "
      + "stroke-linecap='round' stroke-linejoin='round'>"
      + "<path d='M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z'/>"
      + "<path d='M12 9v4'/><path d='M12 17h.01'/></svg>";

    private static final String INFO_ICON =
        "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' "
      + "stroke-linecap='round' stroke-linejoin='round'>"
      + "<circle cx='12' cy='12' r='9'/><path d='M12 11v5'/><path d='M12 8h.01'/></svg>";

    private static FlowPanel container;

    private Notify() {}

    /** Red, attention-grabbing toast for errors and failures. */
    public static void error(String message) {
        show(message, "toast-error", ERROR_ICON);
    }

    /** Neutral toast for informational messages. */
    public static void info(String message) {
        show(message, "toast-info", INFO_ICON);
    }

    private static void show(String message, String variant, String iconSvg) {
        FlowPanel toast = new FlowPanel();
        toast.setStyleName("toast " + variant);

        HTML icon = new HTML(iconSvg);
        icon.setStyleName("toast-icon");
        toast.add(icon);

        InlineLabel text = new InlineLabel(message);
        text.setStyleName("toast-text");
        toast.add(text);

        toast.addDomHandler(e -> dismiss(toast), ClickEvent.getType());
        container().add(toast);

        new Timer() {
            @Override public void run() { dismiss(toast); }
        }.schedule(VISIBLE_MS);
    }

    private static void dismiss(Widget toast) {
        if (toast.getParent() == null) return; // already gone
        toast.addStyleName("toast--out");
        new Timer() {
            @Override public void run() { toast.removeFromParent(); }
        }.schedule(250);
    }

    private static FlowPanel container() {
        if (container == null || !container.isAttached()) {
            container = new FlowPanel();
            container.setStyleName("toast-container");
            RootPanel.get().add(container);
        }
        return container;
    }
}