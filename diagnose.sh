#!/bin/bash
SSH="ssh -i ~/.ssh/pi_deploy_key my-pi"

echo "=== systemd service status ==="
$SSH "systemctl status gameroom --no-pager" 2>&1 || true

echo ""
echo "=== last 50 log lines ==="
$SSH "journalctl -u gameroom -n 150 --no-pager" 2>&1 || true

echo ""
echo "=== errors in journal ==="
$SSH "journalctl -u gameroom --no-pager -r -o cat | grep -E -m 5 -A 3 -B 5 'Z[[:space:]]+(ERROR|WARN)[[:space:]]+[0-9]+' | tac | awk 'NR==1{n=1; printf \"--- error %d ---\n\", n} /^--\$/{n++; printf \"\n--- error %d ---\n\", n; next} /Z[[:space:]]+(ERROR|WARN)/{print \">>> \" \$0; next} {print}'" 2>&1 || true

echo ""
echo "=== jar file ==="
$SSH "ls -lh /opt/gameroom/gameroom.jar" 2>&1 || true

echo ""
echo "=== override config ==="
$SSH "cat /opt/gameroom/application-override.yaml" 2>&1 || true

echo ""
echo "=== java version ==="
$SSH "java -version" 2>&1 || true

echo ""
echo "=== all listening ports and programs ==="
$SSH "sudo ss -tlnp" 2>&1 || true

echo ""
echo "=== disk space ==="
$SSH "df -h /opt" 2>&1 || true

echo ""
echo "=== memory ==="
$SSH "free -h" 2>&1 || true