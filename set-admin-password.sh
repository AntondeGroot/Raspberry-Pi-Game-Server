#!/bin/bash
set -euo pipefail

SSH_KEY=~/.ssh/pi_deploy_key

if [ -n "${1:-}" ]; then
  HOST="$1"
elif timeout 5 ssh -i "$SSH_KEY" -o ConnectTimeout=4 -o BatchMode=yes -o ConnectionAttempts=1 my-pi true 2>/dev/null; then
  HOST=my-pi
else
  echo "⚠️  my-pi unreachable, falling back to my-pi-ext (Cloudflare Tunnel)..."
  HOST=my-pi-ext
fi

echo "Setting admin password on $HOST..."

# Everything runs remotely in one SSH session.
# Single-quoted heredoc ('REMOTE') prevents local variable expansion.
ssh -i "$SSH_KEY" "$HOST" bash << 'REMOTE'
set -euo pipefail

JAR=/opt/gameroom/gameroom.jar
SECRETS=/opt/gameroom/secrets.yaml

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: jar not found at $JAR" >&2
  exit 1
fi

# Generate a random 16-char alphanumeric password
PASSWORD=$(openssl rand -base64 16 | tr -dc 'A-Za-z0-9' | head -c 16)

# Spring Boot never starts in this mode — output is just the hash
HASH=$(java -jar "$JAR" --generate-password="$PASSWORD")

if [[ -z "$HASH" ]]; then
  echo "ERROR: jar did not return a hash" >&2
  exit 1
fi

sudo mkdir -p /opt/gameroom
printf 'admin:\n  password-hash: "%s"\n' "$HASH" | sudo tee "$SECRETS" > /dev/null

# Only the service user can read this file
sudo chown ubuntu:ubuntu "$SECRETS"
sudo chmod 600 "$SECRETS"

echo ""
echo "Admin username: admin"
echo "Admin password : $PASSWORD"
echo "Secrets file   : $SECRETS (mode 600, owner ubuntu)"
echo ""
echo "Restarting gameroom..."
sudo systemctl restart gameroom
echo "Done."
REMOTE