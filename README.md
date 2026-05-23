# Raspberry Pi Game Room

This project was written in GWT in order to get a better understanding of it, since this is used by my employer.
<p align="center">
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/7035146e-9a82-42ae-85d1-6f7a33bca0a2" />
</p>

<p align="center">
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/54ef3dde-b30c-47b6-9569-fbcff16861f3" />
</p>

# Games

## 1. Tock / Keezenspel

<p align="center">
   <a href="https://github.com/AntondeGroot/GWT_Keezenspel"><strong>View Repository</strong></a><br><br>
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/70e1ac8a-416e-4b8a-9593-5207419b20b4" />
</p>

## 2. Qwixx

<p align="center">
   <a href="https://github.com/AntondeGroot/Qwixx"><strong>View Repository</strong></a><br><br>
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/c0e45ab8-958e-4570-a303-cb2ba45c19c4" />
</p>

## Local development

1. Start the backend: `mvn spring-boot:run -pl *-server -am`
2. Start the GWT code server: `mvn gwt:codeserver -pl *-client -am`
3. Open http://localhost:4100/

---
# Deploying to Raspberry Pi
<details>
<summary>Deploying to Raspberry Pi</summary>

---

Run `./deploy.sh` to build, upload, and restart the service.

`deploy.sh` auto-detects the target: it first probes `my-pi` (local network); if unreachable it falls back to `my-pi-ext` (Cloudflare Tunnel). You can also force a target explicitly:

```bash
./deploy.sh           # auto-detect
./deploy.sh my-pi     # force local
./deploy.sh my-pi-ext # force tunnel
```

### First-time Pi setup

Run the setup script:

```bash
./setup-pi.sh
```

This will:
- Install and configure nginx
- Install cloudflared and start it as a service
- Write `/opt/gameroom/games.yaml` with the correct `baseUrl`

The script reads `deploy.local.conf` from the project root to decide which tunnel mode to use:

**Without `deploy.local.conf`** — a temporary public URL is generated, like `https://projector-improving-expired-discussed.trycloudflare.com/`. It changes on every restart but works immediately for quick sessions with friends.

**With `deploy.local.conf`** — a named Cloudflare tunnel is used with a stable custom domain. Set it up first:

1. On the Pi, authenticate and create a named tunnel:
   ```bash
   cloudflared tunnel login
   cloudflared tunnel create gameroom
   cloudflared tunnel route dns gameroom gameroom.yourdomain.com
   ```
   `cloudflared tunnel login` prints a URL — open it on your PC to authenticate. The Pi terminal will then show the tunnel UUID.

2. Create `deploy.local.conf` in the project root (it is gitignored):
   ```
   DOMAINS="gameroom.yourdomain.com"
   TUNNEL_ID=your-tunnel-uuid
   CREDENTIALS_FILE=/home/ubuntu/.cloudflared/your-tunnel-uuid.json

   # Optional: expose SSH over the same tunnel so you can deploy from any network
   SSH_DOMAIN=ssh.yourdomain.com
   ```

3. Run `./setup-pi.sh`

### Deploying from outside your local network (SSH over Cloudflare Tunnel)

If `SSH_DOMAIN` is set in `deploy.local.conf`, `setup-pi.sh` adds an SSH ingress rule to the tunnel. To use it from any network:

1. Add a CNAME DNS record in Cloudflare: `ssh.yourdomain.com` → `<your-tunnel-id>.cfargotunnel.com`

2. Install cloudflared on your Mac (if not already):
   ```bash
   brew install cloudflared
   ```

3. Add a second host alias in `~/.ssh/config`:
   ```
   Host my-pi-ext
       HostName ssh.yourdomain.com
       User ubuntu
       IdentityFile ~/.ssh/pi_deploy_key
       ProxyCommand cloudflared access ssh --hostname %h
   ```

After this, `deploy.sh` will automatically fall back to `my-pi-ext` when `my-pi` is unreachable.

**Keezenspel config** — create `/opt/keezen/application-override.yaml` on the Pi (done by Keezenspel's `setup-pi.sh`):

```yaml
server:
  servlet:
    context-path: /keezen
```
</details>

---
# Creating a new game that integrates with the Game Room
<details>
<summary>Creating a new game that integrates with the Game Room</summary>

---
   
This section documents the contract a game server must fulfil to work with the GameRoom lobby. The GameRoom calls your game server over HTTP; your game does not call the GameRoom directly (except to fetch profile pictures).

## Port & registration

Pick a port that does not clash with existing games and add an entry to `GameRoom-server/src/main/resources/games.yaml` (or `/opt/gameroom/games.yaml` on the Pi):

```yaml
games:
  available:
    - id: mygame
      name: My Game
      baseUrl: http://localhost:4400        # used for server-to-server API calls
      healthUrl: http://localhost:80/mygame/ # used to check if the game is reachable
      minPlayers: 2
      maxPlayers: 4
```

The GameRoom uses `baseUrl` as the root for all API calls to your server. `healthUrl` is polled with a HEAD request to decide whether to show the game in the lobby — typically the nginx path on the Pi. On the Pi, `baseUrl` should use the game's local port (nginx / Cloudflare handles external exposure).

---

## Required REST endpoints

The GameRoom calls the following endpoints on your game server. All request and response bodies are JSON.

### 1. `POST /games` — create a session

Called when the room host starts the game.

**Request body:**
```json
{
  "roomName": "Living Room",
  "maxPlayers": 4,
  "gameOptions": {
    "difficulty": "hard"
  }
}
```

`maxPlayers` is the number of players who actually joined the room (not the configured room maximum). `gameOptions` contains whatever key/value pairs the room host configured in the lobby. Values are typed: `"true"`/`"false"` become booleans, numeric strings become integers, everything else stays a string. It is omitted when no options were set.

**Response body (200):**
```json
{
  "sessionId": "some-unique-id"
}
```

Return a stable ID that identifies this game session for all subsequent calls.

---

### 2. `POST /games/{sessionId}/players` — add a player

Called once per player, in sequence, after the session is created.

**Request body:**
```json
{
  "id": "player-uuid",
  "name": "Alice",
  "profilePic": 3
}
```

`profilePic` is a zero-based global sprite index (see [Profile pictures](#profile-pictures) below). Return any `2xx` status to confirm.

---

### 3. `POST /games/{sessionId}` — start the game

Called after all players have been added. No request body. Return any `2xx` to confirm.

After a successful response the GameRoom redirects each player's browser to:

```
/<gameId>/?sessionid={sessionId}&playerid={playerId}&locale={en|nl|...}
```

`<gameId>` is the `id` field from `games.yaml` (e.g. `/keezen/`). The redirect is a relative path served through nginx — `baseUrl` is used for server-to-server calls only, not for the browser redirect. Use these query parameters to identify who is playing and in which session.

**Error handling:** if steps 2 or 3 fail, the GameRoom immediately sends `DELETE /games/{sessionId}` to clean up. Your server should handle this even for partially-created sessions.

---

### 4. `GET /games/{sessionId}` — session health check

Called every **30 seconds** by the GameRoom to verify the session is still alive.

- Return `2xx` → session is healthy.
- Return any error / connection failure → the GameRoom assumes the session is gone and removes the room from the lobby.

---

### 5. `GET /gamestates/{sessionId}` — game state (for stale detection)

Also polled every **30 seconds**. The GameRoom uses this to detect abandoned games.

**Response body must include a `version` field** that changes whenever the game state changes (e.g. a move counter, a hash, a timestamp):

```json
{
  "version": "42",
  ...
}
```

If `version` stays the same for **60 minutes**, the GameRoom concludes the game is stuck and calls `DELETE /games/{sessionId}` automatically.

---

### 6. `DELETE /games/{sessionId}` — delete a session

Called when the game is stale (60-minute inactivity timeout) or when session creation fails partway through. Free all server-side resources for this session. Return any `2xx` (or even a `404` if it was already gone — the GameRoom ignores the response body).

---

## Optional REST endpoints

### `GET /game-options` — lobby configuration options

Called by the GameRoom when the room host opens the game-options screen. If this endpoint is not implemented (or returns an error), the GameRoom silently skips the options screen and starts the game with no options.

**Response body:** a JSON array of option objects.

```json
[
  {
    "key": "hardMode",
    "labelKey": "gameOption.hardMode",
    "descriptionKey": "gameOption.hardMode.desc",
    "type": "BOOLEAN",
    "defaultValue": "false"
  },
  {
    "key": "color",
    "labelKey": "gameOption.color",
    "choices": ["red", "blue", "green"],
    "defaultValue": "red"
  },
  {
    "key": "rounds",
    "labelKey": "gameOption.rounds",
    "defaultValue": "5"
  }
]
```

The widget rendered in the lobby is determined by the fields present:

| Fields | Widget |
|--------|--------|
| `type: "BOOLEAN"` | Checkbox |
| `choices` array present | Dropdown |
| neither | Text input |

`labelKey` and `descriptionKey` are translation keys resolved from the game's own `GET /i18n/{lang}.json` file. The selected values are sent back as the `gameOptions` map in `POST /games`.

---

## Profile pictures

Players choose a profile picture in the lobby. The selected index is passed to your game as `profilePic` (an integer) in the `POST /games/{sessionId}/players` call.

To render the picture, fetch it directly from the GameRoom:

```
GET http://<gameroom-base-url>/profile-pic/{index}
```

Returns a **100 × 100 PNG**. The image is publicly cacheable for 24 hours (`Cache-Control: public, max-age=86400`), so you can cache it client-side.

---

## Chat window

The GameRoom lobby provides a built-in chat for players waiting in the room. **Games themselves do not need to implement chat** — the lobby handles it before the game starts.

If you want to embed a similar chat inside your game, the GameRoom chat REST API works as follows:

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/chat/{roomId}` | Send a message |
| `GET`  | `/chat/{roomId}` | Fetch all messages |

**Send message body:**
```json
{ "sender": "Alice", "message": "<encrypted text>" }
```

Messages are encrypted with the room ID as the cipher key (see `ChatCipher`). The client polls `GET /chat/{roomId}` every 500 ms.

---

## Summary checklist

| Requirement | Detail |
|-------------|--------|
| `POST /games` | Returns `{ "sessionId": "..." }` |
| `POST /games/{id}/players` | Accepts `id`, `name`, `profilePic` |
| `POST /games/{id}` | Starts game, no body needed |
| `GET /games/{id}` | Returns `2xx` while session is alive |
| `GET /gamestates/{id}` | Returns JSON with a `version` field |
| `DELETE /games/{id}` | Cleans up session resources |
| `GET /game-options` | Optional — returns array of option objects for the lobby |
| Redirect params | Read `?sessionid=`, `?playerid=`, `?locale=` on load |
| Profile pictures | Fetch from `GET <gameroom>/profile-pic/{index}` |
| Port registration | Add `baseUrl` to `games.yaml` |

</details>

---


# Preparing your Raspberry Pi from scratch

<details>
<summary>Preparing your Raspberry Pi from scratch</summary>

## 0. Prerequisite: have Linux installed

## 1. Install Java and basics

```bash
sudo apt update
sudo apt install openjdk-25-jdk ufw
java -version
```

## 2. Create deployment folders

```bash
sudo mkdir -p /opt/keezen /opt/gameroom
sudo chown -R ubuntu:ubuntu /opt/keezen /opt/gameroom
```

## 3. Set up SSH key-based deploy from your laptop

Generate a dedicated deploy key:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/pi_deploy_key
```

Copy the public key to the Pi:

```bash
cat ~/.ssh/pi_deploy_key.pub
```

**On the Pi:**

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
nano ~/.ssh/authorized_keys
# paste the public key, then Ctrl+O → Enter → Ctrl+X
chmod 600 ~/.ssh/authorized_keys
```

Test the connection from your laptop:

```bash
ssh -i ~/.ssh/pi_deploy_key ubuntu@<RASPBERRYPI_IP>
```

Add an SSH alias in `~/.ssh/config`:

```
Host my-pi
    HostName <RASPBERRYPI_IP>
    User ubuntu
    IdentityFile ~/.ssh/pi_deploy_key
```

Then connect with `ssh my-pi`.

## 4. Create systemd services

**`/etc/systemd/system/game-server.service`**

```ini
[Unit]
Description=Java Game Server
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/game-server
ExecStart=/usr/bin/java -jar /opt/game-server/game-server.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

**`/etc/systemd/system/gameroom.service`**

```ini
[Unit]
Description=Gameroom Server
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/gameroom
ExecStart=/usr/bin/java -jar /opt/gameroom/gameroom.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Reload and enable both:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now game-server
sudo systemctl enable --now gameroom
sudo systemctl status game-server gameroom
```

## 5. Verify apps listen on the correct ports

Expected ports: `game-server → 4200`, `gameroom → 4100`

```bash
sudo ss -ltnp | grep -E '4100|4200'
```

You should see both apps listed as `LISTEN`.

> **Note:** If Spring Boot is packaging resources inside the JAR, use `getResourceAsStream()` instead of `new File(getResource(...).toURI())` — the latter fails inside a packaged JAR.

## 6. Deploy scripts

**`deploy-game-server.sh`**

```bash
#!/bin/bash
set -e

echo "Building..."
mvn clean package

echo "Uploading..."
scp -i ~/.ssh/pi_deploy_key GWT_Keezenspel-server/target/GWT_Keezenspel.jar my-pi:/home/ubuntu/game-server.jar

echo "Installing..."
ssh -i ~/.ssh/pi_deploy_key my-pi "sudo mkdir -p /opt/game-server && sudo mv /home/ubuntu/game-server.jar /opt/game-server/game-server.jar"

echo "Restarting..."
ssh -i ~/.ssh/pi_deploy_key my-pi "sudo systemctl restart game-server"

echo "Done."
```

**`deploy-gameroom.sh`**

```bash
#!/bin/bash
set -e

echo "Building..."
mvn clean package

echo "Uploading..."
scp -i ~/.ssh/pi_deploy_key GWT_Keezenspel-server/target/GWT_Keezenspel.jar my-pi:/home/ubuntu/gameroom.jar

echo "Installing..."
ssh -i ~/.ssh/pi_deploy_key my-pi "sudo mkdir -p /opt/gameroom && sudo mv /home/ubuntu/gameroom.jar /opt/gameroom/gameroom.jar"

echo "Restarting..."
ssh -i ~/.ssh/pi_deploy_key my-pi "sudo systemctl restart gameroom"

echo "Done."
```

Make both scripts executable:

```bash
chmod +x deploy-game-server.sh deploy-gameroom.sh
```

## 7. Check logs

```bash
# Follow live
journalctl -u game-server -f
journalctl -u gameroom -f

# Last 100 lines (no pager)
journalctl -u game-server -n 100 --no-pager
journalctl -u gameroom -n 100 --no-pager
```

## 8. Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 4100/tcp
sudo ufw allow 4200/tcp
sudo ufw enable
sudo ufw status
```

## 9. Test on your local network first

From another device on the same network:

```bash
curl http://<PI_IP>:4100
curl http://<PI_IP>:4200
```

If it fails:
- Make sure both devices are on the same network (not guest Wi-Fi)
- Verify the Pi's IP with `hostname -I`

## 10. Why it may still fail from outside your house (CGNAT)

Even if the app works locally it may be unreachable from the internet. This happens when your ISP uses CGNAT: the public IP seen on the internet differs from your router's WAN IP, which makes port forwarding unreliable. The solution is a tunnel — see step 11.

## 11. Expose publicly with Cloudflare Tunnel

Install cloudflared on the Pi (ARM64):

```bash
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb
sudo dpkg -i cloudflared-linux-arm64.deb
sudo apt-get install -f
cloudflared --version
```

Start a temporary tunnel:

```bash
cloudflared tunnel --url http://localhost:4100  # gameroom
cloudflared tunnel --url http://localhost:4200  # game-server
```

This gives a public `trycloudflare.com` URL immediately — no domain needed. It bypasses CGNAT but the URL is temporary and changes on restart.

## 12. Caddy (optional, if you have a domain)

Caddy can act as a reverse proxy in front of both apps. If you have a domain with proper public routing, configure it like this:

```
game.example.com {
    reverse_proxy 127.0.0.1:4200
}

gameroom.example.com {
    reverse_proxy 127.0.0.1:4100
}
```

## 13. Useful commands

```bash
# Check what's listening
sudo ss -ltnp | grep java

# Service management
sudo systemctl status game-server gameroom
sudo systemctl restart game-server gameroom

# Live logs
journalctl -u game-server -f
journalctl -u gameroom -f
```

</details>
