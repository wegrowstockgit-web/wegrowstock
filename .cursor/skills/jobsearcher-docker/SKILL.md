---
name: jobsearcher-docker
description: >-
  Operate the local JobSearcher Docker stack (postgres, api, frontend). Use when
  reading container logs, checking health, rebuilding services, exec into
  containers, or diagnosing deploy issues. Run commands from the repo root via
  scripts/docker_cli.py or docker-ops.bat.
---

# JobSearcher Docker Operations

## Prerequisites

- Docker Desktop running on the host
- Project root: `c:\Users\ranit\Documents\JobSearcher` (or workspace root)
- Compose project name: `jobsearcher`

**Always `cd` to repo root before running commands.**

## Primary CLI (use this)

```powershell
cd c:\Users\ranit\Documents\JobSearcher
python scripts/docker_cli.py <command>
```

Or: `docker-ops.bat <command>`

| Command | Purpose |
|---------|---------|
| `status` | List containers (`docker compose ps -a`) |
| `inspect` | Parsed status; add `--service api --tail 100` for logs |
| `logs api --tail 200` | API logs (use `-f` to follow) |
| `logs frontend` | Frontend/nginx logs |
| `logs postgres` | Postgres logs |
| `health` | Hit `http://localhost:8000/health` |
| `up --build` | Build and start stack detached |
| `down` | Stop stack (`--volumes` wipes DB) |
| `rebuild api` | No-cache rebuild one service |
| `exec api python -c "..."` | Run command in container |

## Raw compose (when CLI is not enough)

```powershell
docker compose ps
docker compose logs api --tail 150
docker compose logs -f api frontend
docker compose up -d --build api
docker compose exec -it api bash
docker compose exec api playwright install chromium
```

## Service map

| Service | Port | Image |
|---------|------|-------|
| frontend | 5173 → 80 | nginx + Vite build |
| api | 8000 | Python 3.12 + Playwright Chromium |
| postgres | internal | pgvector/pg16 |

## Agent workflow for debugging

1. `python scripts/docker_cli.py status` — are containers up?
2. `python scripts/docker_cli.py health` — API responding?
3. `python scripts/docker_cli.py inspect --service api --tail 150` — recent errors
4. If build failed: `python scripts/docker_cli.py rebuild api` or full `rebuild`
5. Playwright missing browsers: rebuild `api` (Dockerfile runs `playwright install`)

## Do not

- Run `docker compose down -v` unless user wants data wiped
- Assume stack is running — check `status` first
- Use interactive `-it` exec in non-TTY agent shells; use `exec -T` via compose
