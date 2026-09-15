#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${HOME_COMPANION_API_KEY:?Export a groceries READ or READ_WRITE key}"
ca=certificates/home-companion-root.crt
url=https://home-companion.home.arpa
curl --fail --silent --show-error --cacert "$ca" "$url/app/login" >/dev/null
# Pass the Authorization header through stdin so it is not present in curl's argv.
printf 'Authorization: Bearer %s\n' "$HOME_COMPANION_API_KEY" | curl --fail --silent --show-error --cacert "$ca" -H @- -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' --data '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' "$url/mcp"
printf '\n'
