package ADG.services.logs;

/**
 * A single log line for the admin page.
 *
 * @param app         app id it belongs to (gameroom / keezen / qwixx)
 * @param level       ERROR | WARN | INFO
 * @param epochMillis timestamp for sorting and "x ago" rendering
 * @param time        ISO-8601 timestamp (display fallback)
 * @param msg         one-line summary
 * @param detail      multi-line remainder (e.g. a stack trace), or null
 * @param status      HTTP status code when this came from the access log, else null
 */
public record LogEntryDto(String app, String level, long epochMillis, String time,
                          String msg, String detail, Integer status) {
}