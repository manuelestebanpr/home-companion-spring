#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Explicit provider avoids differences caused by a separately installed Docker Compose.
export PODMAN_COMPOSE_PROVIDER=podman-compose
exec podman compose --env-file .env -f compose.yaml "$@"
