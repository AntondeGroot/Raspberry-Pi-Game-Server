package ADG.services.logs;

/** Liveness of one app for the "Services" health strip: up | degraded | down. */
public record AppHealthDto(String id, String name, String health) {
}