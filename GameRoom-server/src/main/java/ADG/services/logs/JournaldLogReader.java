package ADG.services.logs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads a single systemd unit's journal via {@code journalctl -o json}. The unit
 * name and --since argument always come from the allowlist / fixed maps — never
 * from raw request input — so this can only ever read whitelisted units.
 *
 * Requires the server process to be in the {@code systemd-journal} group
 * (granted via SupplementaryGroups in gameroom.service); no sudo is used.
 */
@Component
public class JournaldLogReader {

    private static final Logger log = LoggerFactory.getLogger(JournaldLogReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LINES = 500;
    private static final int TIMEOUT_SECONDS = 10;

    // Defense-in-depth allowlists validated at the exec boundary. Callers already
    // pass config-derived unit names and mapped --since strings (never raw request
    // input), but these guards make that guarantee explicit and tool-verifiable, and
    // block anything shaped like a flag (leading '-') or containing shell/arg metachars.
    private static final Pattern SAFE_UNIT = Pattern.compile("[A-Za-z0-9@._:][A-Za-z0-9@._:-]{0,127}");
    private static final Pattern SAFE_SINCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ]{0,31}");

    /**
     * @param appId       owning app id (for the returned entries)
     * @param unit        systemd unit name (allowlist)
     * @param maxPriority journald priority: 3=err, 4=warning, 6=info (lower = more severe)
     * @param sinceArg    fixed --since argument (allowlist), e.g. "1 hour ago"
     */
    public List<LogEntryDto> read(String appId, String unit, int maxPriority, String sinceArg) {
        List<LogEntryDto> entries = new ArrayList<>();
        if (!isSafeUnit(unit) || !isSafeSince(sinceArg)) {
            log.warn("journalctl args rejected by allowlist (unit={}, since={})", unit, sinceArg);
            return entries;
        }

        ProcessBuilder pb = new ProcessBuilder(
                "journalctl", "-u", unit, "-p", String.valueOf(maxPriority),
                "-o", "json", "--no-pager", "--since", sinceArg,
                "-n", String.valueOf(MAX_LINES));
        try {
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    LogEntryDto e = parseLine(appId, line);
                    if (e != null) entries.add(e);
                }
            }
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("journalctl timed out for unit {}", unit);
            }
        } catch (Exception ex) {
            log.warn("journalctl failed for unit {}: {}", unit, ex.getMessage());
        }
        return entries;
    }

    /** A systemd unit name: no shell/arg metacharacters, and never flag-shaped (leading '-'). */
    static boolean isSafeUnit(String unit) {
        return unit != null && SAFE_UNIT.matcher(unit).matches();
    }

    /** A --since argument: letters/digits/spaces only (e.g. "1 hour ago"); rejects flags and metachars. */
    static boolean isSafeSince(String sinceArg) {
        return sinceArg != null && SAFE_SINCE.matcher(sinceArg).matches();
    }

    private LogEntryDto parseLine(String appId, String json) {
        try {
            JsonNode n = MAPPER.readTree(json);
            JsonNode messageNode = n.get("MESSAGE");
            if (messageNode == null || !messageNode.isTextual()) return null; // skip binary blobs
            String message = messageNode.asText();

            int priority = asInt(n.get("PRIORITY"), 6);
            long micros = asLong(n.get("__REALTIME_TIMESTAMP"), 0L);
            long epochMillis = micros / 1000L;
            String level = priority <= 3 ? "ERROR" : priority <= 5 ? "WARN" : "INFO";

            // Split the first line (summary) from the rest (stack trace / detail).
            String msg = message;
            String detail = null;
            int nl = message.indexOf('\n');
            if (nl >= 0) {
                msg = message.substring(0, nl);
                detail = message.substring(nl + 1);
            }
            return new LogEntryDto(appId, level, epochMillis, iso(epochMillis), msg, detail, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static int asInt(JsonNode node, int fallback) {
        if (node == null || node.isNull()) return fallback;
        try { return Integer.parseInt(node.asText()); } catch (NumberFormatException e) { return fallback; }
    }

    private static long asLong(JsonNode node, long fallback) {
        if (node == null || node.isNull()) return fallback;
        try { return Long.parseLong(node.asText()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String iso(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis).toString() : "";
    }
}