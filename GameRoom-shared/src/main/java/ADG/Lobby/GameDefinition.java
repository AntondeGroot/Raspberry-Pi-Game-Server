package ADG.Lobby;

import com.google.gwt.user.client.rpc.IsSerializable;

public class GameDefinition implements IsSerializable {

    private String id;
    private String name;
    private String baseUrl;
    private String healthUrl;
    /** systemd unit name (e.g. "keezen.service") — used by the admin logs page to
     *  read this game's journald logs. Null when the game isn't a local systemd service. */
    private String unit;
    private int minPlayers;
    private int maxPlayers;
    /** True when the game server exposes a /settings?embed=1 page that can be
     *  shown in an iframe inside the GameOptions dialog. */
    private boolean embeddedSettings = false;

    public GameDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getHealthUrl() { return healthUrl != null ? healthUrl : baseUrl; }
    public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }

    public boolean isEmbeddedSettings() { return embeddedSettings; }
    public void setEmbeddedSettings(boolean embeddedSettings) { this.embeddedSettings = embeddedSettings; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
}