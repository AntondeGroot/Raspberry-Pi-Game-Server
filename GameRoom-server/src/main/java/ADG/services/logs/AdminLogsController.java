package ADG.services.logs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /admin/logs — read-only view of journald + nginx logs for the whitelisted
 * apps. Guarded by Spring Security (/admin/** requires ADMIN). Every request
 * parameter is mapped through a fixed allowlist before it reaches a command or
 * the filesystem, so the client can never widen what is read.
 */
@RestController
@RequestMapping("/admin")
public class AdminLogsController {

    /** since -> journalctl --since argument. */
    private static final Map<String, String> SINCE_ARG = Map.of(
            "15m", "15 min ago", "1h", "1 hour ago", "24h", "1 day ago", "7d", "7 days ago");
    /** since -> window length in millis (for the nginx cutoff). */
    private static final Map<String, Long> SINCE_MILLIS = Map.of(
            "15m", 900_000L, "1h", 3_600_000L, "24h", 86_400_000L, "7d", 604_800_000L);
    /** level -> journald max priority (lower number = more severe). */
    private static final Map<String, Integer> LEVEL_PRIORITY = Map.of("err", 3, "warn", 4, "info", 6);
    /** level -> minimum severity rank to keep. */
    private static final Map<String, Integer> LEVEL_MIN_RANK = Map.of("err", 3, "warn", 2, "info", 1);
    private static final Map<String, Integer> SEVERITY_RANK = Map.of("ERROR", 3, "WARN", 2, "INFO", 1);
    private static final Set<String> HTTP_CLASSES = Set.of("4xx", "5xx");
    private static final int MAX_ENTRIES = 500;

    private final LogAppRegistry registry;
    private final JournaldLogReader journald;
    private final NginxAccessLogReader nginx;
    private final HealthChecker health;

    public AdminLogsController(LogAppRegistry registry, JournaldLogReader journald,
                              NginxAccessLogReader nginx, HealthChecker health) {
        this.registry = registry;
        this.journald = journald;
        this.nginx = nginx;
        this.health = health;
    }

    @GetMapping("/logs")
    public LogsResponse logs(
            @RequestParam(name = "app", defaultValue = "all") String app,
            @RequestParam(name = "level", defaultValue = "warn") String level,
            @RequestParam(name = "since", defaultValue = "1h") String since,
            @RequestParam(name = "http", defaultValue = "") String http) {

        String sinceArg = SINCE_ARG.getOrDefault(since, SINCE_ARG.get("1h"));
        long cutoffMillis = System.currentTimeMillis() - SINCE_MILLIS.getOrDefault(since, 3_600_000L);
        int priority = LEVEL_PRIORITY.getOrDefault(level, 4);
        int minRank = LEVEL_MIN_RANK.getOrDefault(level, 2);
        Set<String> httpFilter = parseHttp(http);

        List<LogApp> scope = resolveScope(app);

        List<LogEntryDto> collected = new ArrayList<>();
        for (LogApp a : scope) {
            collected.addAll(journald.read(a.id(), a.unit(), priority, sinceArg));
        }
        collected.addAll(nginx.read(scope, cutoffMillis));

        List<LogEntryDto> entries = filterAndSort(collected, minRank, httpFilter);
        return new LogsResponse(healthOfAllApps(), entries);
    }

    private List<LogApp> resolveScope(String app) {
        if ("all".equals(app)) return registry.all();
        LogApp one = registry.byId(app);
        return one == null ? List.of() : List.of(one);
    }

    private List<LogEntryDto> filterAndSort(List<LogEntryDto> collected, int minRank, Set<String> httpFilter) {
        List<LogEntryDto> entries = new ArrayList<>();
        for (LogEntryDto e : collected) {
            if (SEVERITY_RANK.getOrDefault(e.level(), 1) < minRank) continue;
            if (!httpFilter.isEmpty()) {
                String cls = e.status() == null ? null : (e.status() / 100) + "xx";
                if (cls == null || !httpFilter.contains(cls)) continue;
            }
            entries.add(e);
        }
        entries.sort((x, y) -> Long.compare(y.epochMillis(), x.epochMillis()));
        return entries.size() > MAX_ENTRIES ? new ArrayList<>(entries.subList(0, MAX_ENTRIES)) : entries;
    }

    private List<AppHealthDto> healthOfAllApps() {
        // Probe every app concurrently — sequential probes made each request wait on the
        // sum of all timeouts (up to ~3s each), which was the main tab-switch slowness.
        List<CompletableFuture<AppHealthDto>> futures = registry.all().stream()
                .map(a -> CompletableFuture.supplyAsync(
                        () -> new AppHealthDto(a.id(), a.name(), health.check(a.healthUrl()))))
                .collect(Collectors.toList());
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    private Set<String> parseHttp(String http) {
        Set<String> result = new HashSet<>();
        if (http == null || http.isBlank()) return result;
        for (String part : http.split(",")) {
            String c = part.trim();
            if (HTTP_CLASSES.contains(c)) result.add(c);
        }
        return result;
    }
}