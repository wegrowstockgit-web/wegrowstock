#!/usr/bin/env bash
# InventorySystem deploy helper for macOS / Linux (parity with deploy.bat).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

CMD="${1:-deploy}"
if [[ "$CMD" == "--no-chatbot" || "$CMD" == "--with-chatbot" || "$CMD" == "--clean-frontend" ]]; then
  CMD="deploy"
fi

canonicalize_target() {
  case "${1:-}" in
    pos|frontend-pos|frontend_pos) echo pos ;;
    wms|frontend|frontend-wms|frontend_wms|web) echo wms ;;
    admin|frontend-admin|frontend_admin) echo admin ;;
    frontends|ui|spa) echo frontends ;;
    backend|api|wms-api|app) echo backend ;;
    admin-api|backend-admin) echo admin-api ;;
    gateway|api-gateway) echo gateway ;;
    apis|backends) echo apis ;;
    all|"") echo "" ;;
    *) echo "" ;;
  esac
}

is_known_target() {
  case "${1:-}" in
    pos|frontend-pos|frontend_pos|wms|frontend|frontend-wms|frontend_wms|web|admin|frontend-admin|frontend_admin|frontends|ui|spa|backend|api|wms-api|app|admin-api|backend-admin|gateway|api-gateway|apis|backends|all)
      return 0 ;;
    *) return 1 ;;
  esac
}

if is_known_target "$CMD"; then
  TARGET="$(canonicalize_target "$CMD")"
  CMD="deploy"
else
  TARGET=""
fi

DEPLOY_LOG="$ROOT/.deploy-last.log"
export COMPOSE_ANSI="${COMPOSE_ANSI:-never}"
export DOCKER_CLI_HINTS="${DOCKER_CLI_HINTS:-false}"
WMS_FRONTEND="frontends/apps/frontend_wms"
ADMIN_FRONTEND="frontends/apps/frontend_admin"
POS_FRONTEND="frontends/apps/frontend_pos"
CHATBOT_MARKER="$ROOT/.invsys-chatbot-disabled"

banner() { printf '\n======================================================================\n  %s\n======================================================================\n' "$1"; }
step() { printf '\n -- %s\n' "$1"; }
ok() { printf '    [OK]   %s\n' "$1"; }
info() { printf '    %s\n' "$1"; }
warn() { printf '    [WARN] %s\n' "$1"; }
err() { printf '    [ERROR] %s\n' "$1"; }

fail_with_log() {
  err "$1"
  if [[ -f "$DEPLOY_LOG" ]]; then
    info "Last 40 lines of deploy log:"
    echo
    tail -n 40 "$DEPLOY_LOG" || true
    echo
    info "Full log: $DEPLOY_LOG"
  fi
  exit 1
}

require_docker() {
  if ! docker info >/dev/null 2>&1; then
    err "Docker is not running. Start Docker Desktop / the daemon and try again."
    exit 1
  fi
}

undeploy() {
  step "Stopping existing stack"
  echo "===== undeploy $(date) =====" >> "$DEPLOY_LOG"
  for c in invsys-web invsys-admin-web invsys-pos-web invsys-api invsys-admin-api invsys-api-gateway invsys-db invsys-minio invsys-minio-init; do
    if docker inspect "$c" >/dev/null 2>&1; then
      docker stop "$c" >> "$DEPLOY_LOG" 2>&1 || true
      docker rm "$c" >> "$DEPLOY_LOG" 2>&1 || true
    fi
  done
  docker compose down --remove-orphans >> "$DEPLOY_LOG" 2>&1 || true
  ok "Previous containers removed"
}

ensure_jwt_keys() {
  if [[ -f ops/jwt/dev-private.pem && -f ops/jwt/dev-public.pem ]]; then
    ok "JWT keys present (ops/jwt/)"
    return 0
  fi
  step "Generating persistent dev JWT keys"
  mkdir -p ops/jwt
  (
    cd backend
    mvn -q -Dtest=JwtKeyExportTest -Dsurefire.failIfNoSpecifiedTests=false test -pl invsys-app -am
  ) >> "$DEPLOY_LOG" 2>&1 || fail_with_log "Failed to generate JWT keys."
  ok "JWT keys written to ops/jwt/"
}

apply_chatbot_env() {
  CHATBOT_MODE="enabled"
  [[ -f "$CHATBOT_MARKER" ]] && CHATBOT_MODE="disabled"
  for arg in "$@"; do
    case "$arg" in
      --no-chatbot) CHATBOT_MODE="disabled" ;;
      --with-chatbot) CHATBOT_MODE="enabled" ;;
    esac
  done
  if [[ "$CHATBOT_MODE" == "disabled" ]]; then
    export INVSYS_WITH_CHATBOT=false INVSYS_CHATBOT_ENABLED=false VITE_ENABLE_CHATBOT=false
  else
    export INVSYS_WITH_CHATBOT=true INVSYS_CHATBOT_ENABLED=true VITE_ENABLE_CHATBOT=true
  fi
}

sync_one_frontend_chatbot() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  if [[ -f "$dir/scripts/resolve-chatbot.mjs" ]]; then
    (
      cd "$dir"
      if [[ "${VITE_ENABLE_CHATBOT:-true}" == "false" ]]; then
        node scripts/resolve-chatbot.mjs --disable
      else
        node scripts/resolve-chatbot.mjs --enable
      fi
    ) >> "$DEPLOY_LOG" 2>&1 || true
    return 0
  fi
  if [[ -f "$dir/scripts/resolve-modules.mjs" ]]; then
    (
      cd "$dir"
      if [[ "${VITE_ENABLE_CHATBOT:-true}" == "false" ]]; then
        node scripts/resolve-modules.mjs --disable-chatbot
      else
        node scripts/resolve-modules.mjs --enable-chatbot
      fi
    ) >> "$DEPLOY_LOG" 2>&1 || true
  fi
}

print_endpoints() {
  cat <<'EOF'
  Data plane (WMS)
    UI        http://localhost:3000
    API       http://localhost:8080
    Swagger   http://localhost:8080/swagger-ui.html
  Retail POS (offline-first register)
    UI        http://localhost:3003
    API       POST /api/v1/pos/sync-receipts via :8080
  Control plane (Super Admin)
    UI        http://localhost:3002
    API       http://localhost:8081
    Host hint Host: admin.invsys.com on gateway :8081
  Observability / data
    Grafana   http://localhost:3001   (admin / admin)
    Mailpit   http://localhost:8025   (SMTP 1025, demo tenant only)
    Postgres  localhost:5432
    PgBouncer localhost:6432
EOF
}

print_status_table() {
  echo "  Containers"
  echo "  ----------------------------------------------------------------------"
  docker compose ps -a --format '{{.Name}}|{{.Status}}' 2>/dev/null | while IFS='|' read -r name status; do
    printf '  %-22s %s\n' "$name" "$status"
  done
}

clean_one_frontend() {
  local fe="$1"
  if [[ ! -f "$fe/package.json" ]]; then
    info "Skip clean (missing): $fe"
    return 0
  fi
  step "Cleaning $fe"
  rm -rf "$fe/node_modules" "$fe/dist" "$fe/.vite"
  rm -f "$fe/tsconfig.tsbuildinfo" "$fe/tsconfig.app.tsbuildinfo" "$fe/tsconfig.node.tsbuildinfo"
  ok "Cleaned $fe"
}

clean_frontend() {
  step "Cleaning frontend artifacts (WMS + admin + POS monorepo)"
  if [[ ! -f "$WMS_FRONTEND/package.json" && ! -f "$ADMIN_FRONTEND/package.json" ]]; then
    err "No frontend package.json found under frontends/apps. Run from repo root."
    exit 1
  fi
  clean_one_frontend "$WMS_FRONTEND"
  clean_one_frontend "$ADMIN_FRONTEND"
  clean_one_frontend "$POS_FRONTEND"
  rm -rf frontends/node_modules
  ok "Frontend clean complete"
}

wait_health() {
  local container="$1" label="$2" retries="$3"
  local health="unknown"
  while (( retries > 0 )); do
    health="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo unknown)"
    if [[ "$health" == "healthy" ]]; then
      ok "$container is healthy"
      return 0
    fi
    echo "    ... $container: $health  ($retries checks left)"
    retries=$((retries - 1))
    sleep 5
  done
  warn "$label health timed out — check: docker compose logs ${container/invsys-/} --tail 80"
}

pick_deploy_target() {
  if [[ -n "${TARGET:-}" ]]; then
    return 0
  fi
  local arg
  for arg in "$@"; do
    [[ "$arg" == --* || "$arg" == "deploy" ]] && continue
    if ! is_known_target "$arg"; then
      err "Unknown deploy target: $arg"
      info "Try: pos  wms  admin  frontends  backend  admin-api  gateway  apis"
      exit 1
    fi
    TARGET="$(canonicalize_target "$arg")"
    return 0
  done
}

resolve_deploy_target() {
  SERVICES=""
  TARGET_LABEL=""
  WAIT_CONTAINERS=()
  CLEAN_DIRS=()
  NEED_CHATBOT=0
  case "$TARGET" in
    pos)
      SERVICES="frontend-pos"
      TARGET_LABEL="Retail POS UI (:3003)"
      CLEAN_DIRS=("$POS_FRONTEND")
      ;;
    wms)
      SERVICES="frontend"
      TARGET_LABEL="WMS UI (:3000)"
      CLEAN_DIRS=("$WMS_FRONTEND")
      NEED_CHATBOT=1
      ;;
    admin)
      SERVICES="frontend-admin"
      TARGET_LABEL="Admin UI (:3002)"
      CLEAN_DIRS=("$ADMIN_FRONTEND")
      ;;
    frontends)
      SERVICES="frontend frontend-admin frontend-pos"
      TARGET_LABEL="All frontends (WMS + admin + POS)"
      CLEAN_DIRS=("$WMS_FRONTEND" "$ADMIN_FRONTEND" "$POS_FRONTEND")
      NEED_CHATBOT=1
      ;;
    backend)
      SERVICES="backend"
      TARGET_LABEL="WMS API (invsys-api / :8080)"
      WAIT_CONTAINERS=(invsys-api)
      NEED_CHATBOT=1
      ;;
    admin-api)
      SERVICES="backend-admin"
      TARGET_LABEL="Admin API (invsys-admin-api / :8081)"
      WAIT_CONTAINERS=(invsys-admin-api)
      ;;
    gateway)
      SERVICES="api-gateway"
      TARGET_LABEL="API gateway (:8080 / :8081)"
      WAIT_CONTAINERS=(invsys-api-gateway)
      ;;
    apis)
      SERVICES="backend backend-admin api-gateway"
      TARGET_LABEL="WMS API + Admin API + gateway"
      WAIT_CONTAINERS=(invsys-api invsys-admin-api invsys-api-gateway)
      NEED_CHATBOT=1
      ;;
    *)
      err "Could not resolve deploy target: $TARGET"
      exit 1
      ;;
  esac
}

print_target_endpoints() {
  case "$TARGET" in
    pos) echo "  Retail POS  http://localhost:3003" ;;
    wms) echo "  WMS UI      http://localhost:3000" ;;
    admin) echo "  Admin UI    http://localhost:3002" ;;
    frontends)
      echo "  WMS UI      http://localhost:3000"
      echo "  Admin UI    http://localhost:3002"
      echo "  Retail POS  http://localhost:3003"
      ;;
    backend) echo "  WMS API     http://localhost:8080" ;;
    admin-api) echo "  Admin API   http://localhost:8081" ;;
    gateway) echo "  Gateway     http://localhost:8080  and  :8081" ;;
    apis)
      echo "  WMS API     http://localhost:8080"
      echo "  Admin API   http://localhost:8081"
      ;;
    *) print_endpoints ;;
  esac
}

cmd_deploy_partial() {
  resolve_deploy_target
  banner "InventorySystem deploy ($TARGET_LABEL)"
  require_docker
  ok "Docker is available"
  info "Target: $TARGET  services: $SERVICES"
  info "Existing stack is left running; only these images are rebuilt."
  apply_chatbot_env "$@"
  if (( NEED_CHATBOT )); then
    if [[ "$CHATBOT_MODE" == "disabled" ]]; then
      info "Chatbot: DISABLED"
    else
      info "Chatbot: ENABLED"
    fi
    sync_one_frontend_chatbot "$WMS_FRONTEND"
  fi
  for arg in "$@"; do
    if [[ "$arg" == "--clean-frontend" ]]; then
      for dir in "${CLEAN_DIRS[@]}"; do
        clean_one_frontend "$dir"
      done
    fi
  done
  {
    echo "===== deploy $TARGET $(date) ====="
    echo "services=$SERVICES"
  } > "$DEPLOY_LOG"
  step "Building $SERVICES (quiet — details in log)"
  info "Log file: $DEPLOY_LOG"
  # shellcheck disable=SC2086
  docker compose build --quiet $SERVICES >> "$DEPLOY_LOG" 2>&1 || fail_with_log "Image build failed for $SERVICES."
  ok "Images built"
  step "Recreating $SERVICES"
  # shellcheck disable=SC2086
  docker compose up -d --no-deps --quiet-pull $SERVICES >> "$DEPLOY_LOG" 2>&1 || fail_with_log "Failed to start $SERVICES."
  ok "Containers updated"
  local container
  for container in "${WAIT_CONTAINERS[@]+"${WAIT_CONTAINERS[@]}"}"; do
    step "Waiting for $container health"
    wait_health "$container" "$container" 24
  done
  banner "Partial deploy complete"
  print_target_endpoints
  echo
  print_status_table
  echo
}

cmd_deploy() {
  pick_deploy_target "$@"
  if [[ -n "${TARGET:-}" ]]; then
    cmd_deploy_partial "$@"
    return
  fi

  banner "InventorySystem deploy"
  require_docker
  ok "Docker is available"
  apply_chatbot_env "$@"
  if [[ "$CHATBOT_MODE" == "disabled" ]]; then
    info "Chatbot: DISABLED (WMS backend module omitted + frontend_wms stub)"
  else
    info "Chatbot: ENABLED (WMS with-chatbot + frontend Co-Pilot)"
  fi
  info "Planes: data=app.invsys.com/:8080  pos=:3003  control=admin.invsys.com/:8081"

  for arg in "$@"; do
    if [[ "$arg" == "--clean-frontend" ]]; then
      clean_frontend
    fi
  done

  {
    echo "===== deploy $(date) ====="
    echo "chatbot_mode=$CHATBOT_MODE"
    echo "INVSYS_WITH_CHATBOT=$INVSYS_WITH_CHATBOT"
    echo "VITE_ENABLE_CHATBOT=$VITE_ENABLE_CHATBOT"
  } > "$DEPLOY_LOG"

  sync_one_frontend_chatbot "$WMS_FRONTEND"
  ensure_jwt_keys
  undeploy

  step "Building images (quiet — details in log)"
  info "Log file: $DEPLOY_LOG"
  docker compose build --quiet >> "$DEPLOY_LOG" 2>&1 || fail_with_log "Image build failed."
  ok "Images built"

  step "Starting containers"
  docker compose up -d --remove-orphans --quiet-pull >> "$DEPLOY_LOG" 2>&1 || fail_with_log "Failed to start containers."
  ok "Containers started"

  step "Waiting for WMS API health"
  wait_health invsys-api "WMS API" 36
  step "Waiting for Admin API health"
  wait_health invsys-admin-api "Admin API" 24

  banner "Deploy complete"
  if [[ "$CHATBOT_MODE" == "disabled" ]]; then
    info "Chatbot: DISABLED on this stack"
  else
    info "Chatbot: ENABLED on this stack"
  fi
  print_endpoints
  echo
  print_status_table
  echo
  info "Next:  ./deploy.sh seed     (demo users / password123)"
  info "       ./deploy.sh status"
  info "Chatbot: ./deploy.sh chatbot-enable / chatbot-disable"
  echo
}

cmd_down() {
  banner "InventorySystem undeploy"
  require_docker
  echo "===== down $(date) =====" > "$DEPLOY_LOG"
  undeploy
  echo
  ok "Stack stopped. Database volume preserved (invsys_pgdata)."
  info "Wipe DB volume: docker compose down -v"
  echo
}

cmd_status() {
  banner "InventorySystem status"
  require_docker
  print_status_table
  echo
  print_endpoints
  echo
}

cmd_seed() {
  banner "Load demo seed"
  require_docker
  if ! docker inspect invsys-db >/dev/null 2>&1; then
    err "invsys-db is not running. Run ./deploy.sh deploy first."
    exit 1
  fi
  echo "===== seed $(date) =====" > "$DEPLOY_LOG"
  step "Applying ops/demo_seed.sql"
  docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -q -f /seed/demo_seed.sql >> "$DEPLOY_LOG" 2>&1 \
    || fail_with_log "Demo seed failed."
  ok "Base demo seed applied"
  if [[ -f ops/demo_seed_tenants_extra.sql ]]; then
    step "Applying extra tenant seed"
    docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -q -f /seed/demo_seed_tenants_extra.sql >> "$DEPLOY_LOG" 2>&1 \
      || fail_with_log "Extra tenant seed failed."
    ok "Extra tenants applied"
  fi
  echo
  ok "Seed complete"
  info "WMS login:   owner@demo.test / password123"
  info "POS register: http://localhost:3003  (same tenant login; checkout is offline-first)"
  info "Admin login: owner@demo.test / password123  (platform_admins; UI :3002)"
  info "Floor PIN (after opening Fulfillment): 1234"
  echo
}

cmd_chatbot_enable() {
  banner "Enable Support Co-Pilot / chatbot"
  rm -f "$CHATBOT_MARKER"
  export INVSYS_WITH_CHATBOT=true INVSYS_CHATBOT_ENABLED=true VITE_ENABLE_CHATBOT=true
  echo "===== chatbot-enable $(date) =====" >> "$DEPLOY_LOG"
  sync_one_frontend_chatbot "$WMS_FRONTEND"
  ok "Chatbot ENABLED for WMS backend + frontend_wms"
  info "Redeploy to apply:  ./deploy.sh deploy"
}

cmd_chatbot_disable() {
  banner "Disable Support Co-Pilot / chatbot"
  cat > "$CHATBOT_MARKER" <<'EOF'
Chatbot/training disabled for InventorySystem deploy.
Backend: Maven -P-with-chatbot + INVSYS_CHATBOT_ENABLED=false
Frontend WMS: VITE_ENABLE_CHATBOT=false + stub bridge
EOF
  export INVSYS_WITH_CHATBOT=false INVSYS_CHATBOT_ENABLED=false VITE_ENABLE_CHATBOT=false
  echo "===== chatbot-disable $(date) =====" >> "$DEPLOY_LOG"
  sync_one_frontend_chatbot "$WMS_FRONTEND"
  ok "Chatbot DISABLED for WMS backend + frontend_wms"
  info "Redeploy to apply:  ./deploy.sh deploy"
}

cmd_chatbot_status() {
  banner "Chatbot status"
  if [[ -f "$CHATBOT_MARKER" ]]; then
    info "Preference: DISABLED  [.invsys-chatbot-disabled present]"
  else
    info "Preference: ENABLED   [default - no disable marker]"
  fi
  info "Affects next ./deploy.sh deploy for WMS api + web images."
}

cmd_help() {
  cat <<'EOF'

InventorySystem deploy helper (macOS / Linux)

Usage: ./deploy.sh [command] [options]

Commands:
  deploy                     Rebuild and start the full stack (quiet console)
  deploy <target>            Rebuild only one plane (does not stop the rest)
  deploy --clean-frontend    Clean frontend artifacts, then deploy
  deploy --no-chatbot        Deploy without Support Co-Pilot (WMS api + web)
  deploy --with-chatbot      Deploy with Support Co-Pilot (overrides marker)
  down                       Stop and remove containers (keeps DB volume)
  undeploy                   Alias for down
  status                     Compact container status + URLs
  clean-frontend             Remove frontend_wms / frontend_admin / frontend_pos caches
  seed                       Load demo SQL
  chatbot-enable             Persistently ENABLE chatbot for next deploys
  chatbot-disable            Persistently DISABLE chatbot for next deploys
  chatbot-status             Show whether chatbot is enabled or disabled
  help                       Show this help

Targets (also valid as the first argument, e.g. ./deploy.sh pos):
  pos / frontend-pos         Retail POS UI  invsys-pos-web  :3003
  wms / frontend / web       WMS UI         invsys-web      :3000
  admin / frontend-admin     Admin UI       invsys-admin-web :3002
  frontends / ui             All three SPAs
  backend / api / wms-api    WMS API        invsys-api      :8080
  admin-api / backend-admin  Admin API      invsys-admin-api :8081
  gateway / api-gateway      Nginx gateway  :8080 / :8081
  apis / backends            WMS API + Admin API + gateway

Planes:
  Data plane     frontend_wms + invsys-app via gateway :8080
  Retail POS     frontend_pos + invsys-pos-api via gateway :8080 (:3003)
  Control plane  frontend_admin + invsys-admin-api via gateway :8081

On failure, the last 40 lines of .deploy-last.log are printed.

EOF
}

case "$CMD" in
  help|-h|--help) cmd_help ;;
  deploy) cmd_deploy "$@" ;;
  down|undeploy) cmd_down ;;
  status) cmd_status ;;
  clean-frontend|clean) clean_frontend ;;
  seed) cmd_seed ;;
  chatbot-enable|enable-chatbot) cmd_chatbot_enable ;;
  chatbot-disable|disable-chatbot) cmd_chatbot_disable ;;
  chatbot-status) cmd_chatbot_status ;;
  *) cmd_help; exit 1 ;;
esac
