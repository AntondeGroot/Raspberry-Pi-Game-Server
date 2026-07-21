package ADG.services.logs;

import java.util.List;

/** Payload of GET /admin/logs: per-app health plus the filtered, sorted log entries. */
public record LogsResponse(List<AppHealthDto> apps, List<LogEntryDto> entries) {
}