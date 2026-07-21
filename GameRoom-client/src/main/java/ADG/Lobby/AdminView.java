package ADG.Lobby;

import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;

/**
 * The admin "Ops · Error Logs" screen. Deliberately neutral/utilitarian — it is an
 * internal ops tool, not part of the game UI, so its labels are plain English rather
 * than going through the game's i18n bundle.
 *
 * The view only builds the shell and exposes empty containers + controls; the
 * {@link AdminPresenter} fills the health strip, tabs, filter segments and log rows.
 */
public class AdminView extends Composite {

    private final FlowPanel root = new FlowPanel();
    private final FlowPanel healthContainer = new FlowPanel();
    private final FlowPanel tabsContainer = new FlowPanel();
    private final FlowPanel levelSegment = new FlowPanel();
    private final FlowPanel rangeSegment = new FlowPanel();
    private final FlowPanel httpSegment = new FlowPanel();
    private final FlowPanel listContainer = new FlowPanel();
    private final Label footer = new Label();

    private final Button backButton = new Button("← Lobby");
    private final Button refreshButton = new Button("⟳"); // ⟳
    private final CheckBox autoRefresh = new CheckBox("Auto-refresh");
    private final FlowPanel spinner = new FlowPanel();

    public AdminView() {
        root.setStyleName("admin-logs-root");
        root.add(buildHeader());

        healthContainer.setStyleName("admin-health-strip");
        root.add(healthContainer);

        tabsContainer.setStyleName("admin-tabs");
        root.add(tabsContainer);

        root.add(buildToolbar());

        listContainer.setStyleName("admin-list");
        root.add(listContainer);

        footer.setStyleName("admin-footer");
        root.add(footer);

        initWidget(root);
    }

    private FlowPanel buildHeader() {
        FlowPanel header = new FlowPanel();
        header.setStyleName("admin-header");

        HTML title = new HTML("<h1 class=\"admin-h1\">Ops · Error Logs</h1>"
                + "<p class=\"admin-sub\">Read-only. Whitelisted apps only — GameRoom &amp; installed games.</p>");
        header.add(title);

        FlowPanel controls = new FlowPanel();
        controls.setStyleName("admin-header-controls");
        spinner.setStyleName("admin-spinner");
        spinner.setVisible(false);
        spinner.getElement().setTitle("Loading…");
        autoRefresh.setValue(true);
        autoRefresh.setStyleName("admin-auto");
        refreshButton.setStyleName("admin-icon-btn");
        refreshButton.getElement().setTitle("Refresh now");
        backButton.setStyleName("admin-text-btn");
        controls.add(spinner);
        controls.add(autoRefresh);
        controls.add(refreshButton);
        controls.add(backButton);
        header.add(controls);
        return header;
    }

    private FlowPanel buildToolbar() {
        FlowPanel toolbar = new FlowPanel();
        toolbar.setStyleName("admin-toolbar");
        levelSegment.setStyleName("admin-segment");
        rangeSegment.setStyleName("admin-segment");
        httpSegment.setStyleName("admin-segment");
        httpSegment.getElement().setTitle("HTTP status class");
        toolbar.add(levelSegment);
        toolbar.add(rangeSegment);
        toolbar.add(httpSegment);
        return toolbar;
    }

    public FlowPanel getHealthContainer() { return healthContainer; }
    public FlowPanel getTabsContainer() { return tabsContainer; }
    public FlowPanel getLevelSegment() { return levelSegment; }
    public FlowPanel getRangeSegment() { return rangeSegment; }
    public FlowPanel getHttpSegment() { return httpSegment; }
    public FlowPanel getListContainer() { return listContainer; }
    public Label getFooter() { return footer; }
    public Button getBackButton() { return backButton; }
    public Button getRefreshButton() { return refreshButton; }
    public CheckBox getAutoRefresh() { return autoRefresh; }

    /** Show/hide the header spinner while a real fetch is in flight. */
    public void setLoading(boolean loading) { spinner.setVisible(loading); }

    public void showAlert(String message) { ADG.Utils.Notify.error(message); }
}