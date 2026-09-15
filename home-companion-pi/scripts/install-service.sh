#!/bin/sh
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run with sudo.' >&2; exit 1; }
cd "$(dirname "$0")/.."
root=$(pwd -P)
case "$root" in *' '*|*'%'*) echo 'Use a checkout path without spaces or % for systemd.' >&2; exit 1;; esac
command -v podman-compose >/dev/null
[ -f .env ] || { echo 'Run configure.py first.' >&2; exit 1; }
# systemd owns startup/shutdown; Podman restart policies handle process exits.
cat > /etc/systemd/system/home-companion.service <<EOF
[Unit]
Description=Home Companion Podman Compose
Wants=network-online.target
After=network-online.target
[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$root
ExecStart=$root/scripts/compose.sh up -d
ExecStop=$root/scripts/compose.sh stop
TimeoutStartSec=300
TimeoutStopSec=120
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now home-companion.service
