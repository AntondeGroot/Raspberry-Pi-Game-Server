package ADG.services.logs;

import ADG.Lobby.GameDefinition;
import ADG.config.GamesConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The hardcoded allowlist of apps whose logs the admin page may read. The
 * browser only ever sends an app id; this registry resolves it to a fixed
 * systemd unit and health URL. There is no way to name any other unit or path.
 */
@Component
public class LogAppRegistry {

    private final GamesConfig gamesConfig;
    private final String gameroomUnit;
    private final String gameroomHealthUrl;

    public LogAppRegistry(GamesConfig gamesConfig,
                          @Value("${admin.logs.gameroom-unit:gameroom.service}") String gameroomUnit,
                          @Value("${admin.logs.gameroom-health-url:http://localhost:4100/}") String gameroomHealthUrl) {
        this.gamesConfig = gamesConfig;
        this.gameroomUnit = gameroomUnit;
        this.gameroomHealthUrl = gameroomHealthUrl;
    }

    /** gameroom first, then every game that declares a systemd unit. */
    public List<LogApp> all() {
        Map<String, LogApp> apps = new LinkedHashMap<>();
        apps.put("gameroom", new LogApp("gameroom", "GameRoom", gameroomUnit, gameroomHealthUrl));
        for (GameDefinition g : gamesConfig.getAvailable()) {
            if (g.getUnit() != null && !g.getUnit().isBlank()) {
                apps.put(g.getId(), new LogApp(g.getId(), g.getName(), g.getUnit(), g.getHealthUrl()));
            }
        }
        return new ArrayList<>(apps.values());
    }

    /** Resolve a client-supplied app id against the allowlist, or null if unknown. */
    public LogApp byId(String id) {
        return all().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }
}