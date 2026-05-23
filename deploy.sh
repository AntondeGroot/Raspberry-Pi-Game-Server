#!/bin/bash
set -e

if [ -n "$1" ]; then
  TARGET="$1"
elif timeout 5 ssh -i ~/.ssh/pi_deploy_key -o ConnectTimeout=4 -o BatchMode=yes -o ConnectionAttempts=1 my-pi true 2>/dev/null; then
  TARGET=my-pi
else
  echo "⚠️  my-pi unreachable, falling back to my-pi-ext (Cloudflare Tunnel)..."
  TARGET=my-pi-ext
fi
SSH="ssh -i ~/.ssh/pi_deploy_key $TARGET"
SCP="scp -i ~/.ssh/pi_deploy_key"

stamp_css_version_for_cloudflare_cache_invalidation() {
  local v=$(date +%s)
  html_files=(
    GameRoom-server/src/main/resources/public/index.html
    GameRoom-server/src/main/resources/public/login.html
  )

  restore_html() {
    for f in "${html_files[@]}"; do
      [ -f "$f.bak" ] && mv "$f.bak" "$f"
    done
  }
  trap restore_html EXIT

  for f in "${html_files[@]}"; do
    sed -i.bak "s/\.css\"/.css?v=$v\"/g" "$f"
  done
}

stamp_css_version_for_cloudflare_cache_invalidation

echo "🔨 Building..."
mvn clean package

echo "📦 Uploading..."
$SCP GameRoom-server/target/GameRoom.jar $TARGET:/home/ubuntu/gameroom.jar
$SCP GameRoom-server/src/main/resources/room-names.txt $TARGET:/home/ubuntu/room-names.txt

echo "📁 Installing..."
$SSH "sudo mkdir -p /opt/gameroom && sudo mv /home/ubuntu/gameroom.jar /opt/gameroom/gameroom.jar && sudo cp /home/ubuntu/room-names.txt /opt/gameroom/room-names.txt && rm -f /home/ubuntu/room-names.txt"

echo "⚙️ Ensuring systemd service exists..."
$SSH "
if [ ! -f /etc/systemd/system/gameroom.service ]; then
  sudo tee /etc/systemd/system/gameroom.service > /dev/null << 'EOF'
[Unit]
Description=GameRoom
After=network.target

[Service]
User=ubuntu
ExecStart=/usr/bin/java --add-opens java.base/java.lang=ALL-UNNAMED -jar /opt/gameroom/gameroom.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF
  sudo systemctl daemon-reload
  sudo systemctl enable gameroom
fi"

echo "🔄 Restarting..."
$SSH "sudo systemctl restart gameroom"

echo "⏳ Waiting for server to come up..."
$SSH "
  for i in \$(seq 1 60); do
    sleep 2
    if curl -sf http://localhost:4100/ > /dev/null 2>&1; then
      echo \"✅ Server is up, waiting for tunnel to reconnect...\"
      sleep 7
      echo \"✅ Ready.\"
      exit 0
    fi
  done
  echo \"❌ Server did not come up after 120 seconds.\"
  systemctl status gameroom --no-pager
  exit 1
"