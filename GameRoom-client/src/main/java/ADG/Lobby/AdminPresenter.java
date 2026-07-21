package ADG.Lobby;

import ADG.Presenter;
import ADG.PresenterManager;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives the admin logs screen: builds the filter controls, fetches
 * GET /admin/logs, and renders the health strip, app tabs and log rows.
 */
public class AdminPresenter implements Presenter {

    private static final int REFRESH_MS = 10_000;
    private static final DateTimeFormat TIME_FMT = DateTimeFormat.getFormat("HH:mm:ss");

    private static final String[][] LEVELS = {{"err", "Errors only"}, {"warn", "Warnings +"}, {"info", "Info +"}};
    private static final String[][] RANGES = {{"15m", "15m"}, {"1h", "1h"}, {"24h", "24h"}, {"7d", "7d"}};
    private static final String[][] HTTP = {{"4xx", "4xx"}, {"5xx", "5xx"}};

    private final AdminView view;
    private final PresenterManager presenterManager;

    private String app = "all";
    private String level = "warn";
    private String since = "1h";
    private final List<String> httpFilter = new ArrayList<>();

    private final Map<String, String> appNames = new LinkedHashMap<>();
    // Last server payload, kept so switching app tabs re-filters locally (instant)
    // instead of round-tripping to the server.
    private final List<AppInfo> lastApps = new ArrayList<>();
    private final List<Entry> lastEntries = new ArrayList<>();
    private Timer timer;

    public AdminPresenter(AdminView view, PresenterManager presenterManager) {
        this.view = view;
        this.presenterManager = presenterManager;
        // Persistent controls live on the reused view, so wire them once here rather
        // than in start() (which runs on every visit and would stack handlers).
        view.getBackButton().addClickHandler(e -> presenterManager.switchToLobby());
        view.getRefreshButton().addClickHandler(e -> load(true));
        view.getAutoRefresh().addValueChangeHandler(e -> applyAutoRefresh());
    }

    @Override
    public void start() {
        History.newItem("admin");
        renderSegment(view.getLevelSegment(), LEVELS, () -> level, id -> { level = id; reloadWithSegments(); });
        renderSegment(view.getRangeSegment(), RANGES, () -> since, id -> { since = id; reloadWithSegments(); });
        renderHttpSegment();

        load(true);
        applyAutoRefresh();
    }

    @Override
    public void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    private void reloadWithSegments() {
        renderSegment(view.getLevelSegment(), LEVELS, () -> level, id -> { level = id; reloadWithSegments(); });
        renderSegment(view.getRangeSegment(), RANGES, () -> since, id -> { since = id; reloadWithSegments(); });
        load(true);
    }

    private void applyAutoRefresh() {
        if (timer != null) { timer.cancel(); timer = null; }
        if (view.getAutoRefresh().getValue()) {
            // Background refresh — no spinner, so it doesn't blink every interval.
            timer = new Timer() { @Override public void run() { load(false); } };
            timer.scheduleRepeating(REFRESH_MS);
        }
    }

    // ---------------------------------------------------------------- filters

    private void renderSegment(FlowPanel container, String[][] options,
                               java.util.function.Supplier<String> active, Consumer<String> onSelect) {
        container.clear();
        for (String[] opt : options) {
            Button b = new Button(opt[1]);
            b.setStyleName("admin-seg-btn");
            if (opt[0].equals(active.get())) b.addStyleName("admin-seg-btn--active");
            final String id = opt[0];
            b.addClickHandler(e -> onSelect.accept(id));
            container.add(b);
        }
    }

    private void renderHttpSegment() {
        view.getHttpSegment().clear();
        for (String[] opt : HTTP) {
            Button b = new Button(opt[1]);
            b.setStyleName("admin-seg-btn");
            if (httpFilter.contains(opt[0])) b.addStyleName("admin-seg-btn--active");
            final String id = opt[0];
            b.addClickHandler(e -> {
                if (httpFilter.contains(id)) httpFilter.remove(id); else httpFilter.add(id);
                renderHttpSegment();
                load(true);
            });
            view.getHttpSegment().add(b);
        }
    }

    // ------------------------------------------------------------------ fetch

    private void load(boolean showSpinner) {
        // Always fetch the full set (app=all); the app tabs filter it client-side so
        // switching tabs is instant and never hits the server.
        String url = "/admin/logs?app=all&level=" + level + "&since=" + since
                + "&http=" + httpParam();
        if (showSpinner) view.setLoading(true);
        RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
        rb.setHeader("Accept", "application/json");
        try {
            rb.sendRequest(null, new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    view.setLoading(false);
                    int code = response.getStatusCode();
                    if (code == Response.SC_UNAUTHORIZED || code == Response.SC_FORBIDDEN) {
                        view.showAlert("Not authorised — please log in as admin.");
                        presenterManager.switchToLobby();
                        return;
                    }
                    if (code >= 200 && code < 300) {
                        renderResponse(response.getText());
                    } else {
                        GWT.log("admin/logs: unexpected status " + code);
                    }
                }
                @Override
                public void onError(Request request, Throwable exception) {
                    view.setLoading(false);
                    GWT.log("admin/logs: fetch error: " + exception.getMessage());
                }
            });
        } catch (RequestException e) {
            view.setLoading(false);
            GWT.log("admin/logs: request error: " + e.getMessage());
        }
    }

    private String httpParam() {
        StringBuilder sb = new StringBuilder();
        for (String c : httpFilter) {
            if (sb.length() > 0) sb.append(',');
            sb.append(c);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- render

    private void renderResponse(String json) {
        try {
            JSONObject obj = JSONParser.parseStrict(json).isObject();
            if (obj == null) return;

            lastApps.clear();
            lastApps.addAll(parseApps(obj.get("apps")));
            lastEntries.clear();
            lastEntries.addAll(parseEntries(obj.get("entries")));

            appNames.clear();
            for (AppInfo a : lastApps) appNames.put(a.id, a.name);

            rerender();
        } catch (Exception e) {
            GWT.log("admin/logs: parse error: " + e.getMessage());
        }
    }

    /** Render from the cached payload — used by both a fresh fetch and instant tab switches. */
    private void rerender() {
        List<Entry> shown = filterByApp(lastEntries);
        renderHealth(lastApps);
        renderTabs(lastApps, errorCounts(lastEntries)); // counts across all apps, not just the shown tab
        renderRows(shown);
        updateFooter(shown.size());
    }

    private List<Entry> filterByApp(List<Entry> entries) {
        if ("all".equals(app)) return entries;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (app.equals(e.app)) out.add(e);
        return out;
    }

    private void renderHealth(List<AppInfo> apps) {
        FlowPanel c = view.getHealthContainer();
        c.clear();
        for (AppInfo a : apps) {
            String color = healthColor(a.health);
            FlowPanel item = new FlowPanel();
            item.setStyleName("admin-health-item");
            item.add(inline("admin-health-name", a.name));
            item.add(inline("admin-health-label", "status:"));
            InlineLabel dot = inline("admin-health-dot", "");
            dot.getElement().getStyle().setBackgroundColor(color);
            item.add(dot);
            InlineLabel word = inline("admin-health-word", healthWord(a.health));
            word.getElement().getStyle().setColor(color);
            item.add(word);
            c.add(item);
        }
    }

    private void renderTabs(List<AppInfo> apps, Map<String, Integer> counts) {
        FlowPanel c = view.getTabsContainer();
        c.clear();
        addTab(c, "all", "All apps", counts.getOrDefault("all", 0));
        for (AppInfo a : apps) addTab(c, a.id, a.name, counts.getOrDefault(a.id, 0));
    }

    private void addTab(FlowPanel container, String id, String name, int count) {
        Button tab = new Button();
        String html = "<span class=\"admin-tab-name\">" + escapeHtml(name) + "</span>";
        if (count > 0) html += "<span class=\"admin-count\">" + count + "</span>";
        tab.setHTML(html);
        tab.setStyleName("admin-tab");
        if (id.equals(app)) tab.addStyleName("admin-tab--active");
        tab.addClickHandler(e -> { app = id; rerender(); });
        container.add(tab);
    }

    private void renderRows(List<Entry> entries) {
        FlowPanel c = view.getListContainer();
        c.clear();
        if (entries.isEmpty()) {
            FlowPanel empty = new FlowPanel();
            empty.setStyleName("admin-empty");
            empty.add(new HTML("<div class=\"admin-empty-mark\">✓</div>"));
            empty.add(new InlineLabel(emptyText()));
            c.add(empty);
            return;
        }
        for (Entry e : entries) c.add(buildRow(e));
    }

    private FlowPanel buildRow(Entry e) {
        String lvl = levelClass(e.level);
        FlowPanel row = new FlowPanel();
        row.setStyleName("admin-row");
        row.addStyleName("admin-row--" + lvl);

        FlowPanel head = new FlowPanel();
        head.setStyleName("admin-row-head");
        head.add(inline("admin-time", TIME_FMT.format(new Date(e.epochMillis))));
        head.add(inline("admin-app-tag", appNames.getOrDefault(e.app, e.app)));
        InlineLabel levelTag = inline("admin-level-tag", e.level);
        levelTag.addStyleName("admin-level--" + lvl);
        head.add(levelTag);

        FlowPanel slot = new FlowPanel();
        slot.setStyleName("admin-http-slot");
        if (e.status != null) {
            InlineLabel st = inline("admin-http-tag", String.valueOf(e.status));
            st.addStyleName(e.status >= 500 ? "admin-http--5xx" : "admin-http--4xx");
            slot.add(st);
        }
        head.add(slot);

        head.add(inline("admin-msg", e.msg));
        head.add(inline("admin-ago", formatAgo(e.epochMillis)));

        if (e.detail != null && !e.detail.isEmpty()) {
            final InlineLabel chevron = inline("admin-chevron", "▸");
            head.add(chevron);
            head.addStyleName("admin-row-head--clickable");
            final HTML detail = new HTML("<pre class=\"admin-detail-pre\">" + escapeHtml(e.detail) + "</pre>");
            detail.setStyleName("admin-detail");
            detail.setVisible(false);
            head.addDomHandler(ev -> {
                boolean show = !detail.isVisible();
                detail.setVisible(show);
                chevron.setText(show ? "▾" : "▸");
            }, ClickEvent.getType());
            row.add(head);
            row.add(detail);
        } else {
            row.add(head);
        }
        return row;
    }

    private void updateFooter(int count) {
        String s = count + (count == 1 ? " entry" : " entries") + " · updated " + TIME_FMT.format(new Date());
        view.getFooter().setText(s);
    }

    // ------------------------------------------------------------- text bits

    private String emptyText() {
        String what = "err".equals(level) ? "errors" : "warn".equals(level) ? "errors or warnings" : "log entries";
        String range = labelOf(RANGES, since);
        String forApp = "all".equals(app) ? "" : " for " + appNames.getOrDefault(app, app);
        return "No " + what + " in the last " + range + forApp + ".";
    }

    private static String labelOf(String[][] options, String id) {
        for (String[] o : options) if (o[0].equals(id)) return o[1];
        return id;
    }

    private static String formatAgo(long epochMillis) {
        long s = (System.currentTimeMillis() - epochMillis) / 1000L;
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    private static String levelClass(String level) {
        if ("ERROR".equals(level)) return "error";
        if ("WARN".equals(level)) return "warn";
        return "info";
    }

    private static String healthColor(String health) {
        if ("down".equals(health)) return "#d64545";
        if ("degraded".equals(health)) return "#d4a72c";
        return "#4a9e6d";
    }

    private static String healthWord(String health) {
        if ("down".equals(health)) return "not responding";
        if ("degraded".equals(health)) return "degraded";
        return "running";
    }

    private static InlineLabel inline(String style, String text) {
        InlineLabel label = new InlineLabel(text);
        label.setStyleName(style);
        return label;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --------------------------------------------------------------- parsing

    private Map<String, Integer> errorCounts(List<Entry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!"ERROR".equals(e.level)) continue;
            counts.merge(e.app, 1, Integer::sum);
            counts.merge("all", 1, Integer::sum);
        }
        return counts;
    }

    private List<AppInfo> parseApps(JSONValue val) {
        List<AppInfo> apps = new ArrayList<>();
        JSONArray arr = val == null ? null : val.isArray();
        if (arr == null) return apps;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.get(i).isObject();
            if (o == null) continue;
            apps.add(new AppInfo(str(o, "id"), str(o, "name"), str(o, "health")));
        }
        return apps;
    }

    private List<Entry> parseEntries(JSONValue val) {
        List<Entry> entries = new ArrayList<>();
        JSONArray arr = val == null ? null : val.isArray();
        if (arr == null) return entries;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.get(i).isObject();
            if (o == null) continue;
            Entry e = new Entry();
            e.app = str(o, "app");
            e.level = str(o, "level");
            e.epochMillis = (long) num(o, "epochMillis");
            e.msg = str(o, "msg");
            e.detail = str(o, "detail");
            Integer status = optInt(o, "status");
            e.status = status;
            entries.add(e);
        }
        return entries;
    }

    private static String str(JSONObject o, String key) {
        JSONValue v = o.get(key);
        return (v != null && v.isString() != null) ? v.isString().stringValue() : null;
    }

    private static double num(JSONObject o, String key) {
        JSONValue v = o.get(key);
        return (v != null && v.isNumber() != null) ? v.isNumber().doubleValue() : 0;
    }

    private static Integer optInt(JSONObject o, String key) {
        JSONValue v = o.get(key);
        return (v != null && v.isNumber() != null) ? (int) v.isNumber().doubleValue() : null;
    }

    private static final class AppInfo {
        final String id, name, health;
        AppInfo(String id, String name, String health) { this.id = id; this.name = name; this.health = health; }
    }

    private static final class Entry {
        String app, level, msg, detail;
        long epochMillis;
        Integer status;
    }
}