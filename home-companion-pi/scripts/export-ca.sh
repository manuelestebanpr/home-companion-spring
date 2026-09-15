#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p certificates
id=$(./scripts/compose.sh ps -q caddy)
[ -n "$id" ] || { echo 'Caddy is not running.' >&2; exit 1; }
podman cp "$id:/data/caddy/pki/authorities/local/root.crt" certificates/home-companion-root.crt
chmod 644 certificates/home-companion-root.crt
openssl x509 -in certificates/home-companion-root.crt -noout -fingerprint -sha256
printf '%s\n' 'Exported the public root certificate only. Verify this fingerprint on each client.'
