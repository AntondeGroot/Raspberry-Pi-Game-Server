#!/bin/bash
set -e

SSH="ssh -i ~/.ssh/pi_deploy_key my-pi"
SCP="scp -i ~/.ssh/pi_deploy_key"

CONFIG_FILE="./deploy.local.conf"

USE_NAMED_TUNNEL=false

if [ -s "$CONFIG_FILE" ]; then
  source "$CONFIG_FILE"

  if [ -n "$DOMAINS" ] && [ -n "$TUNNEL_ID" ] && [ -n "$CREDENTIALS_FILE" ]; then
    USE_NAMED_TUNNEL=true
  else
    echo "⚠️  $CONFIG_FILE exists but is missing DOMAINS, TUNNEL_ID, or CREDENTIALS_FILE."
    echo "   Falling back to random trycloudflare tunnel."
  fi
else
  echo "ℹ️  No non-empty $CONFIG_FILE found. Using random trycloudflare tunnel."
fi

echo "📁 Creating directory..."
$SSH "sudo mkdir -p /opt/gameroom"

echo "📦 Installing nginx..."
$SSH "sudo apt-get install -y nginx"

echo "⚙️  Configuring nginx..."
$SCP nginx.conf my-pi:/home/ubuntu/gameroom-nginx.conf
$SSH "sudo mv /home/ubuntu/gameroom-nginx.conf /etc/nginx/sites-available/gameroom && \
      sudo ln -sf /etc/nginx/sites-available/gameroom /etc/nginx/sites-enabled/gameroom && \
      sudo rm -f /etc/nginx/sites-enabled/default && \
      sudo fuser -k 80/tcp || true; sudo nginx -t && sudo systemctl enable nginx && sudo systemctl restart nginx"

echo "⚙️  Installing gameroom systemd service..."
$SSH "sudo tee /etc/systemd/system/gameroom.service > /dev/null << 'EOF'
[Unit]
Description=GameRoom
After=network.target

[Service]
User=ubuntu
ExecStart=/usr/bin/java -jar /opt/gameroom/gameroom.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF"
$SSH "sudo systemctl daemon-reload && sudo systemctl enable gameroom"

echo "☁️  Installing cloudflared..."
$SSH "if ! command -v cloudflared &>/dev/null; then
  curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64 -o /tmp/cloudflared
  sudo mv /tmp/cloudflared /usr/local/bin/cloudflared
  sudo chmod +x /usr/local/bin/cloudflared
fi"

if [ "$USE_NAMED_TUNNEL" = true ]; then
  echo "⚙️  Installing cloudflared named tunnel config for $DOMAINS..."

  TMP_CLOUDFLARED_CONFIG="$(mktemp)"

  INGRESS_BLOCK=""
  for d in $DOMAINS; do
    INGRESS_BLOCK+="  - hostname: $d
    service: http://localhost:80
"
  done
  if [ -n "$SSH_DOMAIN" ]; then
    INGRESS_BLOCK+="  - hostname: $SSH_DOMAIN
    service: ssh://localhost:22
"
  fi
  INGRESS_BLOCK+="  - service: http_status:404"

  cat > "$TMP_CLOUDFLARED_CONFIG" <<EOF
tunnel: $TUNNEL_ID
credentials-file: $CREDENTIALS_FILE

ingress:
$INGRESS_BLOCK
EOF

  $SSH "sudo mkdir -p /etc/cloudflared"
  $SCP "$TMP_CLOUDFLARED_CONFIG" my-pi:/home/ubuntu/cloudflared-config.yml
  rm -f "$TMP_CLOUDFLARED_CONFIG"

  $SSH "sudo mv /home/ubuntu/cloudflared-config.yml /etc/cloudflared/config.yml"

  echo "⚙️  Installing cloudflared named-tunnel systemd service..."
  $SSH "sudo tee /etc/systemd/system/cloudflared.service > /dev/null << 'EOF'
[Unit]
Description=Cloudflare Named Tunnel
After=network.target

[Service]
ExecStart=/usr/local/bin/cloudflared tunnel run
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF"

else
  echo "⚙️  Installing cloudflared quick-tunnel systemd service..."

  $SSH "sudo rm -f /etc/cloudflared/config.yml || true"

  $SSH "sudo tee /etc/systemd/system/cloudflared.service > /dev/null << 'EOF'
[Unit]
Description=Cloudflare Quick Tunnel
After=network.target

[Service]
ExecStart=/usr/local/bin/cloudflared tunnel --url http://localhost:80
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF"
fi

$SSH "sudo systemctl daemon-reload && \
      sudo systemctl enable cloudflared && \
      sudo systemctl restart cloudflared"

echo "📝 Writing games config..."
$SCP GameRoom-server/src/main/resources/games.yaml my-pi:/home/ubuntu/games.yaml
$SSH "sudo mv /home/ubuntu/games.yaml /opt/gameroom/games.yaml"

echo ""
echo "✅ Pi setup complete."
echo "   Run ./deploy.sh to deploy the application."

if [ "$USE_NAMED_TUNNEL" = true ]; then
  for d in $DOMAINS; do
    echo "   Public URL: https://$d"
  done
else
  echo "   To find your public tunnel URL, run: ./get-tunnel-url.sh"
fi

if [ -n "$SSH_DOMAIN" ]; then
  echo ""
  echo "🔑 SSH over Cloudflare Tunnel is configured for: $SSH_DOMAIN"
  echo "   Add a CNAME DNS record in Cloudflare: $SSH_DOMAIN → <your-tunnel-id>.cfargotunnel.com"
  echo ""
  echo "   On your Mac, install cloudflared if you haven't already:"
  echo "     brew install cloudflared"
  echo ""
  echo "   Add this to ~/.ssh/config on your Mac:"
  echo "     Host my-pi-ext"
  echo "       HostName $SSH_DOMAIN"
  echo "       User ubuntu"
  echo "       IdentityFile ~/.ssh/pi_deploy_key"
  echo "       ProxyCommand cloudflared access ssh --hostname %h"
  echo ""
  echo "   Then deploy from any network with: ./deploy.sh my-pi-ext"
fi