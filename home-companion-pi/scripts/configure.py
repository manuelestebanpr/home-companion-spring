#!/usr/bin/env python3
"""Prepare Pi configuration; the owner must supply the administrator password."""
import argparse
import ipaddress
import os
from pathlib import Path
import secrets

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--lan-ip", required=True, help="Stable IPv4 address of this Pi")
parser.add_argument("--upstream-dns", required=True, help="Router or other DNS resolver IPv4")
args = parser.parse_args()
lan = ipaddress.IPv4Address(args.lan_ip)
upstream = ipaddress.IPv4Address(args.upstream_dns)
if not lan.is_private or lan.is_loopback or lan.is_link_local or lan.is_unspecified:
    parser.error("LAN address must be a private, routable IPv4 address")
if upstream == lan or upstream.is_loopback or upstream.is_unspecified:
    parser.error("Upstream DNS must not point back to this Pi")
root = Path(__file__).resolve().parent.parent
lines = [f"LAN_IP={lan}", f"UPSTREAM_DNS={upstream}", "TZ=America/Bogota", "BACKEND_IMAGE=localhost/home-companion:0.1.0", "ADMIN_USERNAME=admin", "ADMIN_PASSWORD="]
lines += [f"{key}={secrets.token_urlsafe(32)}" for key in ("DB_PASSWORD", "PIHOLE_PASSWORD")]
fd = os.open(root / ".env", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as output:
    output.write("\n".join(lines) + "\n")
print("Created .env (mode 0600). Set ADMIN_PASSWORD yourself before starting; it is never generated.")
