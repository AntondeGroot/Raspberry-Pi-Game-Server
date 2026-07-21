package ADG.services.logs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Liveness probe for the "Services" strip. Hits an app's health URL and reports
 * up / degraded / down. All URLs come from the allowlist (games.yaml + config).
 */
@Component
public class HealthChecker {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final long DEGRADED_MILLIS = 1500;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** up = responds < 500 quickly; degraded = responds but slowly; down = 5xx or unreachable. */
    public String check(String healthUrl) {
        if (healthUrl == null || healthUrl.isBlank()) return "down";
        try {
            long start = System.nanoTime();
            HttpRequest req = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            if (res.statusCode() >= 500) return "down";
            return elapsedMillis > DEGRADED_MILLIS ? "degraded" : "up";
        } catch (Exception e) {
            return "down";
        }
    }
}