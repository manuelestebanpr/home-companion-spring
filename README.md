# Home Companion

A shared household pantry: one Java 25 application, a web interface, a phone API, and four MCP tools.

| Resource                         | Start here                                                           |
| -------------------------------- | -------------------------------------------------------------------- |
| Backend, web and admin           | [home-companion-backend/README.md](home-companion-backend/README.md) |
| Raspberry Pi / Podman deployment | [home-companion-pi/README.md](home-companion-pi/README.md)           |
| Phone API contract               | [openapi.json](home-companion-backend/docs/openapi.json)             |
| MCP connection guide             | [MCP.md](home-companion-backend/docs/MCP.md)                         |
| Original architecture            | [architecture.html](docs/architecture.html)                          |

## Local preview

Requires JDK 25 and PostgreSQL 18 (no H2 fallback). With Podman running and `podman-compose` installed, provision the database and run Java manually:

```sh
cd home-companion-backend
cp -n .env.example .env
chmod 600 .env
# Set DB_PASSWORD and ADMIN_PASSWORD in .env first.
PODMAN_COMPOSE_PROVIDER=podman-compose podman compose --env-file .env -f compose.dev.yaml up -d postgres
./mvnw spring-boot:run
```

Java loads the existing `.env` automatically from the backend folder. See the backend README for using an existing PostgreSQL instance.

Open **http://localhost:8080/app** or **http://localhost:8080/admin**. Log in as `admin` using `ADMIN_PASSWORD` you configured in `.env`, then change it. The local MCP endpoint is **http://localhost:8080/mcp**; create a key on the web before connecting.

The Pi deployment uses **https://home-companion.home.arpa** after DNS and certificate setup. No native iOS app is included. The original design remains a reference; implemented scope and differences are recorded in the backend README.
