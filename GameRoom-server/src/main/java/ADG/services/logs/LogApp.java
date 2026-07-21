package ADG.services.logs;

/**
 * One entry in the admin-logs allowlist: an app whose logs may be read.
 * The browser only ever sends {@code id}; the server maps it here to a fixed
 * systemd unit and health-probe URL. Nothing outside this list is reachable.
 */
public record LogApp(String id, String name, String unit, String healthUrl) {
}