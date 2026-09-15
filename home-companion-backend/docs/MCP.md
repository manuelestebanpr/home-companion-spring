# Connect an assistant

## 1. Prepare the server

1. Complete [Pi DNS and certificate setup](../../home-companion-pi/README.md). For local development, use `http://localhost:8080` instead.
2. Sign in on the web and change the bootstrap password.
3. Ensure the owner has an explicit groceries grant: `VIEW` for queries, `EDIT` for additions. This also applies to administrator-owned keys.
4. Open **Claves y dispositivos**. Create a `READ` or `READ_WRITE` key and store the one-time secret in your client's environment as `HOME_COMPANION_API_KEY`.
5. Connect using Streamable HTTP, URL `https://home-companion.home.arpa/mcp`, header `Authorization: Bearer <key>`.

An assistant running outside your household network cannot reach this LAN-only server. No OAuth or cloud relay is configured.

## 2. Protocol compatibility

Spring AI 2.0.1 resolves MCP Java SDK 2.0.0. The integration suite exercises the **2025-11-25** `initialize`, `tools/list` and `tools/call` flow, with no `Mcp-Session-Id`. This is the supported contract for this release.

> [!NOTE]
> The original architecture targets 2026-07-28. That newer revision's discovery and envelope behavior is **not implemented or claimed here**. Stateless transport is separate from protocol revision; use a client compatible with the tested revision. See the [Spring AI 2.0 release notes](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/) for the SDK baseline.

## 3. Codex

Add to your local `~/.codex/config.toml`:

```toml
[mcp_servers.home_companion]
url = "https://home-companion.home.arpa/mcp"
bearer_token_env_var = "HOME_COMPANION_API_KEY"
```

For local development, replace only `url` with `http://localhost:8080/mcp`.

For the household CA, set this before launching Codex:

```sh
export CODEX_CA_CERTIFICATE=/absolute/path/home-companion-root.crt
```

The installed Codex CLI recognizes `CODEX_CA_CERTIFICATE` and the `SSL_CERT_FILE` fallback. Keep the key and CA path in the actual launcher environment. On the Pi deployment, trust the exported household CA on the client and configure its TLS certificate environment if required by your installation. Launch Codex from the process environment containing the key; restart a GUI client after updating its launcher environment. Use `/mcp` to inspect the connection. These settings follow [official Codex MCP documentation](https://developers.openai.com/codex/mcp).

## 4. Claude Code / generic HTTP client

```json
{
  "mcpServers": {
    "home-companion": {
      "type": "http",
      "url": "https://home-companion.home.arpa/mcp",
      "headers": { "Authorization": "Bearer ${HOME_COMPANION_API_KEY}" }
    }
  }
}
```

For Node clients, launch with `NODE_EXTRA_CA_CERTS=/absolute/path/home-companion-root.crt`. Installing the certificate in a browser does not automatically configure every client process. Keep certificate verification enabled.

## 5. Connection check

From `home-companion-pi`, with the key exported and certificate copied:

```sh
./scripts/smoke.sh
```

This checks TLS and lists the tools without changing grocery data. The Java integration tests additionally exercise initialization, additions, denial, revocation and REST/MCP parity. Actual Codex/Claude sessions on your Pi remain a deployment acceptance check.

| Tool                      | Arguments                                                         | Permission        |
| ------------------------- | ----------------------------------------------------------------- | ----------------- |
| `groceries_list_stock`    | `query?`, `limit?` (1–200, default 50), `offset?`                 | VIEW + READ       |
| `groceries_list_desired`  | None                                                              | VIEW + READ       |
| `groceries_shopping_list` | `include_satisfied?`                                              | VIEW + READ       |
| `groceries_add_stock`     | `product_id` or `new_product`, positive `packages`, `request_id?` | EDIT + READ_WRITE |

Query results wrap rows as `{"items":[...]}` in `structuredContent`, matching REST. New-product fields are `item`, `kind` (`COUNT`, `MASS`, `VOLUME`), `packageQuantity`, `unit` (`unit`, `g`, `kg`, `ml`, `l`), optional `brand`, `category`, `attributes`.

Example tool parameters (mutates stock; use intentionally):

```json
{
  "name": "groceries_add_stock",
  "arguments": {
    "new_product": {
      "item": "Leche",
      "kind": "VOLUME",
      "brand": "Alpina",
      "attributes": ["Deslactosada"],
      "packageQuantity": 1,
      "unit": "l"
    },
    "packages": 2,
    "request_id": "milk-purchase-001"
  }
}
```

Reuse `request_id` with the same payload only when retrying that purchase. The stored result is returned for 24 hours. A different purchase needs a new ID. Without an ID, repeated calls add stock again.

## 6. Troubleshooting

| Symptom               | Check                                                                   |
| --------------------- | ----------------------------------------------------------------------- |
| Name does not resolve | Client uses the Pi for DNS; no public secondary bypasses it             |
| Certificate error     | Hostname is exact; CA installed for this client process                 |
| HTTP 401              | Missing, expired or revoked key; phone tokens cannot call MCP           |
| HTTP 403              | Account pending/disabled, bootstrap password unchanged, wrong Origin    |
| Tool `isError: true`  | Owner grant, key cap, module enabled, valid quantities or retry payload |
| Tools unavailable     | Compatible protocol revision; restart client after environment changes  |

Revocation takes effect on the next request, even if the client already listed the tools.
