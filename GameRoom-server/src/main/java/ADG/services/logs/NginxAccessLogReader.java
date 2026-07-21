package ADG.services.logs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads HTTP 4xx/5xx lines from nginx's access log. Expects the JSON
 * {@code log_format} configured in nginx.conf (fields: time, method, path, status).
 * Each request is attributed to a whitelisted app by its path prefix, exactly the
 * way nginx routes: /keezen/* -> keezen, /qwixx/* -> qwixx, everything else -> gameroom.
 *
 * Only the fixed access.log* files in one configured directory are ever read —
 * no request input reaches the filesystem.
 */
@Component
public class NginxAccessLogReader {

    private static final Logger log = LoggerFactory.getLogger(NginxAccessLogReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ENTRIES = 500;
    /** Only look at rotated/gzipped logs when the window reaches beyond this many hours. */
    private static final long ROTATED_THRESHOLD_MILLIS = 20L * 60 * 60 * 1000;

    /**
     * Paths that only internet background-scanners request (probing for exposed
     * secrets/config). Their 4xx responses are noise, so we hide them from the page
     * — but ONLY on a 4xx. A 5xx on any of these stays visible, so we never mask a
     * real server error. Matched case-insensitively as substrings.
     */
    private static final List<String> SCANNER_PATH_TOKENS = List.of(
            "/.env", "/.git", "/.aws", "/.ssh", "/.svn", "/.hg",
            "/wp-", "/wordpress", "/xmlrpc.php", "/phpmyadmin", "/vendor/",
            "/.ds_store", "/.vscode", "/.idea");
    /** Extensions this app never serves — any request for one is a probe. */
    private static final List<String> SCANNER_PATH_SUFFIXES = List.of(
            ".php", ".aspx", ".asp", ".env", ".bak", ".sql");

    private final Path logDir;
    private final String baseName;

    public NginxAccessLogReader(
            @Value("${admin.logs.nginx-dir:/var/log/nginx}") String dir,
            @Value("${admin.logs.nginx-access-log:access.log}") String baseName) {
        this.logDir = Path.of(dir);
        this.baseName = baseName;
    }

    /**
     * @param scope        apps in scope (used for path-prefix attribution)
     * @param cutoffMillis oldest timestamp to include
     */
    public List<LogEntryDto> read(List<LogApp> scope, long cutoffMillis) {
        List<LogEntryDto> entries = new ArrayList<>();
        boolean gameroomInScope = scope.stream().anyMatch(a -> "gameroom".equals(a.id()));
        boolean includeRotated = (System.currentTimeMillis() - cutoffMillis) > ROTATED_THRESHOLD_MILLIS;

        for (Path file : filesToRead(includeRotated)) {
            readFile(file, scope, gameroomInScope, cutoffMillis, entries);
            if (entries.size() >= MAX_ENTRIES) break;
        }
        return entries;
    }

    private List<Path> filesToRead(boolean includeRotated) {
        List<Path> files = new ArrayList<>();
        Path live = logDir.resolve(baseName);
        if (Files.isReadable(live)) files.add(live);
        if (!includeRotated) return files;
        try (var stream = Files.list(logDir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(baseName + ".") && Files.isReadable(p);
                    })
                    .sorted() // access.log.1, access.log.2.gz, ... (newest-ish first)
                    .forEach(files::add);
        } catch (IOException e) {
            log.warn("could not list nginx log dir {}: {}", logDir, e.getMessage());
        }
        return files;
    }

    private void readFile(Path file, List<LogApp> scope, boolean gameroomInScope,
                          long cutoffMillis, List<LogEntryDto> out) {
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = file.getFileName().toString().endsWith(".gz")
                     ? new GZIPInputStream(raw) : raw;
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && out.size() < MAX_ENTRIES) {
                LogEntryDto e = parseLine(line, scope, gameroomInScope, cutoffMillis);
                if (e != null) out.add(e);
            }
        } catch (Exception e) {
            log.warn("could not read nginx log {}: {}", file, e.getMessage());
        }
    }

    private LogEntryDto parseLine(String json, List<LogApp> scope, boolean gameroomInScope, long cutoffMillis) {
        try {
            JsonNode n = MAPPER.readTree(json);
            int status = n.path("status").asInt(0);
            if (status < 400) return null; // this page only surfaces HTTP errors

            long epochMillis = parseTime(n.path("time").asText(null));
            if (epochMillis < cutoffMillis) return null;

            String path = n.path("path").asText("");
            if (isScannerNoise(path, status)) return null;

            String appId = attribute(path, scope, gameroomInScope);
            if (appId == null) return null;

            String method = n.path("method").asText("");
            String level = status >= 500 ? "ERROR" : "WARN";
            String msg = (method.isEmpty() ? "" : method + " ") + path + " → " + status;
            return new LogEntryDto(appId, level, epochMillis, iso(epochMillis), msg, null, status);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True when this is a background-scanner probe we should hide: a known probe
     * path that returned a 4xx. 5xx (and anything below 400) is never treated as
     * noise, so a genuine error on such a path still surfaces.
     */
    static boolean isScannerNoise(String path, int status) {
        if (status < 400 || status >= 500) return false;
        if (path == null) return false;
        String p = path.toLowerCase(java.util.Locale.ROOT);
        for (String token : SCANNER_PATH_TOKENS) if (p.contains(token)) return true;
        for (String suffix : SCANNER_PATH_SUFFIXES) if (p.endsWith(suffix)) return true;
        return false;
    }

    /** Map a request path to a whitelisted app id (games by prefix, else gameroom). */
    private String attribute(String path, List<LogApp> scope, boolean gameroomInScope) {
        for (LogApp a : scope) {
            if (!"gameroom".equals(a.id()) && path.startsWith("/" + a.id() + "/")) {
                return a.id();
            }
        }
        return gameroomInScope ? "gameroom" : null;
    }

    private static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try { return OffsetDateTime.parse(iso).toInstant().toEpochMilli(); }
        catch (Exception e) { return 0L; }
    }

    private static String iso(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : "";
    }
}