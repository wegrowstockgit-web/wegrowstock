@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=deploy"
:: Flag-only invocations (deploy.bat --no-chatbot) are deploy, not help.
if /i "%CMD%"=="--no-chatbot" set "CMD=deploy"
if /i "%CMD%"=="--with-chatbot" set "CMD=deploy"
if /i "%CMD%"=="--clean-frontend" set "CMD=deploy"
set "DEPLOY_LOG=%~dp0.deploy-last.log"
set "COMPOSE_ANSI=never"
set "DOCKER_CLI_HINTS=false"
set "WMS_FRONTEND=frontends\apps\frontend_wms"
set "ADMIN_FRONTEND=frontends\apps\frontend_admin"
set "POS_FRONTEND=frontends\apps\frontend_pos"
set "TARGET="

call :canonicalize_target "%CMD%"
if defined CANON_TARGET (
    set "TARGET=!CANON_TARGET!"
    set "CMD=deploy"
)

if /i "%CMD%"=="help" goto :help
if /i "%CMD%"=="--help" goto :help
if /i "%CMD%"=="-h" goto :help
if /i "%CMD%"=="deploy" goto :deploy
if /i "%CMD%"=="down" goto :down
if /i "%CMD%"=="undeploy" goto :down
if /i "%CMD%"=="status" goto :status
if /i "%CMD%"=="clean-frontend" goto :clean_frontend
if /i "%CMD%"=="clean" goto :clean_frontend
if /i "%CMD%"=="seed" goto :seed
if /i "%CMD%"=="chatbot-enable" goto :chatbot_enable
if /i "%CMD%"=="chatbot-disable" goto :chatbot_disable
if /i "%CMD%"=="chatbot-status" goto :chatbot_status
if /i "%CMD%"=="enable-chatbot" goto :chatbot_enable
if /i "%CMD%"=="disable-chatbot" goto :chatbot_disable
goto :help

:: ---------------------------------------------------------------------------
:: Logging helpers (readable console; details go to .deploy-last.log)
:: ---------------------------------------------------------------------------
:banner
echo.
echo ======================================================================
echo   %~1
echo ======================================================================
exit /b 0

:step
echo.
echo  -- %~1
exit /b 0

:ok
echo     [OK]   %~1
exit /b 0

:info
echo     %~1
exit /b 0

:warn
echo     [WARN] %~1
exit /b 0

:err
echo     [ERROR] %~1
exit /b 0

:fail_with_log
call :err "%~1"
if exist "%DEPLOY_LOG%" (
    call :info "Last 40 lines of deploy log:"
    echo.
    powershell -NoProfile -Command "Get-Content -LiteralPath '%DEPLOY_LOG%' -Tail 40 -ErrorAction SilentlyContinue"
    echo.
    call :info "Full log: %DEPLOY_LOG%"
)
exit /b 1

:require_docker
docker info >nul 2>&1
if errorlevel 1 (
    call :err "Docker is not running. Start Docker Desktop and try again."
    exit /b 1
)
exit /b 0

:undeploy
call :step "Stopping existing stack"
>> "%DEPLOY_LOG%" echo ===== undeploy %DATE% %TIME% =====
for %%c in (invsys-web invsys-admin-web invsys-pos-web invsys-api invsys-admin-api invsys-api-gateway invsys-db invsys-minio invsys-minio-init) do (
    docker inspect %%c >nul 2>&1
    if not errorlevel 1 (
        docker stop %%c >> "%DEPLOY_LOG%" 2>&1
        docker rm %%c >> "%DEPLOY_LOG%" 2>&1
    )
)
docker compose down --remove-orphans >> "%DEPLOY_LOG%" 2>&1
call :ok "Previous containers removed"
exit /b 0

:ensure_jwt_keys
if exist "ops\jwt\dev-private.pem" if exist "ops\jwt\dev-public.pem" (
    call :ok "JWT keys present (ops\jwt\)"
    exit /b 0
)
call :step "Generating persistent dev JWT keys"
if not exist "ops\jwt" mkdir "ops\jwt"
pushd backend
call mvn -q -Dtest=JwtKeyExportTest -Dsurefire.failIfNoSpecifiedTests=false test -pl invsys-app -am >> "%DEPLOY_LOG%" 2>&1
set "KEYGEN_ERR=!errorlevel!"
popd
if !KEYGEN_ERR! neq 0 (
    call :fail_with_log "Failed to generate JWT keys."
    exit /b 1
)
call :ok "JWT keys written to ops\jwt\"
exit /b 0

:: ---------------------------------------------------------------------------
:: Chatbot (WMS backend + frontend_wms together; admin portal is unaffected)
:: Marker: .invsys-chatbot-disabled at repo root
:: ---------------------------------------------------------------------------
:chatbot_marker
set "CHATBOT_MARKER=%~dp0.invsys-chatbot-disabled"
exit /b 0

:apply_chatbot_env
:: Sets INVSYS_WITH_CHATBOT / INVSYS_CHATBOT_ENABLED / VITE_ENABLE_CHATBOT for compose build.
:: Optional override: --no-chatbot | --with-chatbot on the deploy command line.
call :chatbot_marker
set "CHATBOT_MODE=enabled"
if exist "%CHATBOT_MARKER%" set "CHATBOT_MODE=disabled"

set "ARG1=%~1"
set "ARG2=%~2"
set "ARG3=%~3"
if /i "!ARG1!"=="--no-chatbot" set "CHATBOT_MODE=disabled"
if /i "!ARG1!"=="--with-chatbot" set "CHATBOT_MODE=enabled"
if /i "!ARG2!"=="--no-chatbot" set "CHATBOT_MODE=disabled"
if /i "!ARG2!"=="--with-chatbot" set "CHATBOT_MODE=enabled"
if /i "!ARG3!"=="--no-chatbot" set "CHATBOT_MODE=disabled"
if /i "!ARG3!"=="--with-chatbot" set "CHATBOT_MODE=enabled"
if /i "!ARG1!"=="--clean-frontend" (
    if /i "!ARG2!"=="--no-chatbot" set "CHATBOT_MODE=disabled"
    if /i "!ARG2!"=="--with-chatbot" set "CHATBOT_MODE=enabled"
)
if /i "!ARG2!"=="--clean-frontend" (
    if /i "!ARG3!"=="--no-chatbot" set "CHATBOT_MODE=disabled"
    if /i "!ARG3!"=="--with-chatbot" set "CHATBOT_MODE=enabled"
)

if /i "!CHATBOT_MODE!"=="disabled" (
    set "INVSYS_WITH_CHATBOT=false"
    set "INVSYS_CHATBOT_ENABLED=false"
    set "VITE_ENABLE_CHATBOT=false"
) else (
    set "INVSYS_WITH_CHATBOT=true"
    set "INVSYS_CHATBOT_ENABLED=true"
    set "VITE_ENABLE_CHATBOT=true"
)
exit /b 0

:sync_one_frontend_chatbot
:: %~1 = frontend directory with scripts\resolve-chatbot.mjs or resolve-modules.mjs
if not exist "%~1" exit /b 0
if exist "%~1\scripts\resolve-chatbot.mjs" (
    pushd "%~1"
    if /i "%VITE_ENABLE_CHATBOT%"=="false" (
        call node scripts\resolve-chatbot.mjs --disable >> "%DEPLOY_LOG%" 2>&1
    ) else (
        call node scripts\resolve-chatbot.mjs --enable >> "%DEPLOY_LOG%" 2>&1
    )
    popd
    exit /b 0
)
if exist "%~1\scripts\resolve-modules.mjs" (
    pushd "%~1"
    if /i "%VITE_ENABLE_CHATBOT%"=="false" (
        call node scripts\resolve-modules.mjs --disable-chatbot >> "%DEPLOY_LOG%" 2>&1
    ) else (
        call node scripts\resolve-modules.mjs --enable-chatbot >> "%DEPLOY_LOG%" 2>&1
    )
    popd
)
exit /b 0

:sync_frontend_chatbot_bridge
:: Sync chatbot resolve scripts for the WMS monorepo app.
call :sync_one_frontend_chatbot "%WMS_FRONTEND%"
exit /b 0

:chatbot_enable
call :banner "Enable Support Co-Pilot / chatbot"
call :chatbot_marker
if exist "%CHATBOT_MARKER%" del /f /q "%CHATBOT_MARKER%" >nul 2>&1
set "INVSYS_WITH_CHATBOT=true"
set "INVSYS_CHATBOT_ENABLED=true"
set "VITE_ENABLE_CHATBOT=true"
set "DEPLOY_LOG=%~dp0.deploy-last.log"
(
    echo ===== chatbot-enable %DATE% %TIME% =====
) >> "%DEPLOY_LOG%"
call :sync_frontend_chatbot_bridge
call :ok "Chatbot ENABLED for WMS backend + frontend_wms"
call :info "Marker cleared: .invsys-chatbot-disabled"
call :info "Admin portal ^(frontend_admin^) is unaffected"
call :info "Redeploy to apply:  deploy.bat deploy"
echo.
exit /b 0

:chatbot_disable
call :banner "Disable Support Co-Pilot / chatbot"
call :chatbot_marker
(
    echo Chatbot/training disabled for InventorySystem deploy.
    echo Backend: Maven -P-with-chatbot + INVSYS_CHATBOT_ENABLED=false
    echo Frontend WMS: VITE_ENABLE_CHATBOT=false + stub bridge
) > "%CHATBOT_MARKER%"
set "INVSYS_WITH_CHATBOT=false"
set "INVSYS_CHATBOT_ENABLED=false"
set "VITE_ENABLE_CHATBOT=false"
set "DEPLOY_LOG=%~dp0.deploy-last.log"
(
    echo ===== chatbot-disable %DATE% %TIME% =====
) >> "%DEPLOY_LOG%"
call :sync_frontend_chatbot_bridge
call :ok "Chatbot DISABLED for WMS backend + frontend_wms"
call :info "Marker written: .invsys-chatbot-disabled"
call :info "Redeploy to apply:  deploy.bat deploy"
echo.
exit /b 0

:chatbot_status
call :banner "Chatbot status"
call :chatbot_marker
if exist "%CHATBOT_MARKER%" (
    call :info "Preference: DISABLED  [.invsys-chatbot-disabled present]"
) else (
    call :info "Preference: ENABLED   [default - no disable marker]"
)
call :info "Affects next deploy.bat deploy for WMS api + web images."
call :info "Control plane ^(admin^) is independent of this toggle."
echo.
exit /b 0

:canonicalize_target
:: Maps aliases to a canonical target name. Empty = full stack.
set "CANON_TARGET="
if /i "%~1"=="pos" set "CANON_TARGET=pos"
if /i "%~1"=="frontend-pos" set "CANON_TARGET=pos"
if /i "%~1"=="frontend_pos" set "CANON_TARGET=pos"
if /i "%~1"=="wms" set "CANON_TARGET=wms"
if /i "%~1"=="frontend" set "CANON_TARGET=wms"
if /i "%~1"=="frontend-wms" set "CANON_TARGET=wms"
if /i "%~1"=="frontend_wms" set "CANON_TARGET=wms"
if /i "%~1"=="web" set "CANON_TARGET=wms"
if /i "%~1"=="admin" set "CANON_TARGET=admin"
if /i "%~1"=="frontend-admin" set "CANON_TARGET=admin"
if /i "%~1"=="frontend_admin" set "CANON_TARGET=admin"
if /i "%~1"=="frontends" set "CANON_TARGET=frontends"
if /i "%~1"=="ui" set "CANON_TARGET=frontends"
if /i "%~1"=="spa" set "CANON_TARGET=frontends"
if /i "%~1"=="backend" set "CANON_TARGET=backend"
if /i "%~1"=="api" set "CANON_TARGET=backend"
if /i "%~1"=="wms-api" set "CANON_TARGET=backend"
if /i "%~1"=="app" set "CANON_TARGET=backend"
if /i "%~1"=="admin-api" set "CANON_TARGET=admin-api"
if /i "%~1"=="backend-admin" set "CANON_TARGET=admin-api"
if /i "%~1"=="gateway" set "CANON_TARGET=gateway"
if /i "%~1"=="api-gateway" set "CANON_TARGET=gateway"
if /i "%~1"=="apis" set "CANON_TARGET=apis"
if /i "%~1"=="backends" set "CANON_TARGET=apis"
if /i "%~1"=="all" set "CANON_TARGET="
exit /b 0

:pick_deploy_target
if defined TARGET exit /b 0
for %%a in (%*) do (
    set "ARG=%%~a"
    if /i not "!ARG!"=="deploy" if /i not "!ARG:~0,2!"=="--" (
        if not defined TARGET (
            call :canonicalize_target "!ARG!"
            if defined CANON_TARGET set "TARGET=!CANON_TARGET!"
            if not defined CANON_TARGET (
                call :err "Unknown deploy target: !ARG!"
                call :info "Try: pos  wms  admin  frontends  backend  admin-api  gateway  apis"
                exit /b 1
            )
        )
    )
)
exit /b 0

:resolve_deploy_target
set "SERVICES="
set "TARGET_LABEL="
set "WAIT_CONTAINERS="
set "CLEAN_DIRS="
set "NEED_CHATBOT="
if /i "%TARGET%"=="pos" (
    set "SERVICES=frontend-pos"
    set "TARGET_LABEL=Retail POS UI (:3003)"
    set "CLEAN_DIRS=%POS_FRONTEND%"
)
if /i "%TARGET%"=="wms" (
    set "SERVICES=frontend"
    set "TARGET_LABEL=WMS UI (:3000)"
    set "CLEAN_DIRS=%WMS_FRONTEND%"
    set "NEED_CHATBOT=1"
)
if /i "%TARGET%"=="admin" (
    set "SERVICES=frontend-admin"
    set "TARGET_LABEL=Admin UI (:3002)"
    set "CLEAN_DIRS=%ADMIN_FRONTEND%"
)
if /i "%TARGET%"=="frontends" (
    set "SERVICES=frontend frontend-admin frontend-pos"
    set "TARGET_LABEL=All frontends (WMS + admin + POS)"
    set "CLEAN_DIRS=%WMS_FRONTEND% %ADMIN_FRONTEND% %POS_FRONTEND%"
    set "NEED_CHATBOT=1"
)
if /i "%TARGET%"=="backend" (
    set "SERVICES=backend"
    set "TARGET_LABEL=WMS API (invsys-api / :8080)"
    set "WAIT_CONTAINERS=invsys-api"
    set "NEED_CHATBOT=1"
)
if /i "%TARGET%"=="admin-api" (
    set "SERVICES=backend-admin"
    set "TARGET_LABEL=Admin API (invsys-admin-api / :8081)"
    set "WAIT_CONTAINERS=invsys-admin-api"
)
if /i "%TARGET%"=="gateway" (
    set "SERVICES=api-gateway"
    set "TARGET_LABEL=API gateway (:8080 / :8081)"
    set "WAIT_CONTAINERS=invsys-api-gateway"
)
if /i "%TARGET%"=="apis" (
    set "SERVICES=backend backend-admin api-gateway"
    set "TARGET_LABEL=WMS API + Admin API + gateway"
    set "WAIT_CONTAINERS=invsys-api invsys-admin-api invsys-api-gateway"
    set "NEED_CHATBOT=1"
)
if not defined SERVICES (
    call :err "Could not resolve deploy target: %TARGET%"
    exit /b 1
)
exit /b 0

:wait_named_health
:: %~1 container  %~2 retries
set "WAIT_NAME=%~1"
set /a WAIT_RETRIES=%~2
:wait_named_loop
set "WAIT_HEALTH=unknown"
for /f "usebackq delims=" %%s in (`docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" %WAIT_NAME% 2^>nul`) do set "WAIT_HEALTH=%%s"
if /i "!WAIT_HEALTH!"=="healthy" (
    call :ok "%WAIT_NAME% is healthy"
    exit /b 0
)
echo     ... %WAIT_NAME%: !WAIT_HEALTH!  ^(!WAIT_RETRIES! checks left^)
set /a WAIT_RETRIES-=1
if !WAIT_RETRIES! leq 0 (
    call :warn "%WAIT_NAME% health timed out — check: docker compose logs --tail 80"
    exit /b 0
)
timeout /t 5 /nobreak >nul
goto :wait_named_loop

:deploy
call :pick_deploy_target %*
if errorlevel 1 exit /b 1
if defined TARGET goto :deploy_partial

call :banner "InventorySystem deploy"
call :require_docker
if errorlevel 1 exit /b 1
call :ok "Docker is available"

call :apply_chatbot_env %*
if /i "!CHATBOT_MODE!"=="disabled" (
    call :info "Chatbot: DISABLED ^(WMS backend module omitted + frontend_wms stub^)"
) else (
    call :info "Chatbot: ENABLED ^(WMS with-chatbot + frontend Co-Pilot^)"
)
call :info "Planes: data=app.invsys.com/:8080  control=admin.invsys.com/:8081"

if /i "%~1"=="--clean-frontend" (
    call :clean_frontend
    if errorlevel 1 exit /b 1
)
if /i "%~2"=="--clean-frontend" (
    call :clean_frontend
    if errorlevel 1 exit /b 1
)
if /i "%~3"=="--clean-frontend" (
    call :clean_frontend
    if errorlevel 1 exit /b 1
)

(
    echo ===== deploy %DATE% %TIME% =====
    echo chatbot_mode=!CHATBOT_MODE!
    echo INVSYS_WITH_CHATBOT=!INVSYS_WITH_CHATBOT!
    echo VITE_ENABLE_CHATBOT=!VITE_ENABLE_CHATBOT!
) > "%DEPLOY_LOG%"

call :sync_frontend_chatbot_bridge

call :ensure_jwt_keys
if errorlevel 1 exit /b 1

call :undeploy

call :step "Building images (quiet — details in log)"
call :info "Log file: %DEPLOY_LOG%"
docker compose build --quiet >> "%DEPLOY_LOG%" 2>&1
if errorlevel 1 (
    call :fail_with_log "Image build failed."
    exit /b 1
)
call :ok "Images built"

call :step "Starting containers"
docker compose up -d --remove-orphans --quiet-pull >> "%DEPLOY_LOG%" 2>&1
if errorlevel 1 (
    call :fail_with_log "Failed to start containers."
    exit /b 1
)
call :ok "Containers started"

call :step "Waiting for WMS API health"
set /a RETRIES=36
set "API_HEALTH=unknown"
:wait_health
set "API_HEALTH=unknown"
for /f "usebackq delims=" %%s in (`docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" invsys-api 2^>nul`) do set "API_HEALTH=%%s"
if /i "!API_HEALTH!"=="healthy" (
    call :ok "invsys-api is healthy"
    goto :wait_admin_health
)
echo     ... invsys-api: !API_HEALTH!  ^(!RETRIES! checks left^)
set /a RETRIES-=1
if !RETRIES! leq 0 (
    call :warn "WMS API health timed out — check: docker compose logs backend --tail 80"
    goto :wait_admin_health
)
timeout /t 5 /nobreak >nul
goto :wait_health

:wait_admin_health
call :step "Waiting for Admin API health"
set /a RETRIES=24
set "ADMIN_HEALTH=unknown"
:wait_admin
set "ADMIN_HEALTH=unknown"
for /f "usebackq delims=" %%s in (`docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" invsys-admin-api 2^>nul`) do set "ADMIN_HEALTH=%%s"
if /i "!ADMIN_HEALTH!"=="healthy" (
    call :ok "invsys-admin-api is healthy"
    goto :deploy_summary
)
echo     ... invsys-admin-api: !ADMIN_HEALTH!  ^(!RETRIES! checks left^)
set /a RETRIES-=1
if !RETRIES! leq 0 (
    call :warn "Admin API health timed out — check: docker compose logs backend-admin --tail 80"
    goto :deploy_summary
)
timeout /t 5 /nobreak >nul
goto :wait_admin

:deploy_summary
call :banner "Deploy complete"
if /i "!CHATBOT_MODE!"=="disabled" (
    call :info "Chatbot: DISABLED on this stack"
) else (
    call :info "Chatbot: ENABLED on this stack"
)
call :print_endpoints
echo.
call :print_status_table
echo.
call :info "Next:  deploy.bat seed     ^(demo users / password123^)"
call :info "       deploy.bat status"
call :info "Chatbot: deploy.bat chatbot-enable / chatbot-disable"
echo.
exit /b 0

:deploy_partial
call :resolve_deploy_target
if errorlevel 1 exit /b 1
call :banner "InventorySystem deploy (%TARGET_LABEL%)"
call :require_docker
if errorlevel 1 exit /b 1
call :ok "Docker is available"
call :info "Target: %TARGET%  services: %SERVICES%"
call :info "Existing stack is left running; only these images are rebuilt."

call :apply_chatbot_env %*
if defined NEED_CHATBOT (
    if /i "!CHATBOT_MODE!"=="disabled" (
        call :info "Chatbot: DISABLED"
    ) else (
        call :info "Chatbot: ENABLED"
    )
    call :sync_frontend_chatbot_bridge
)

if /i "%~1"=="--clean-frontend" call :clean_target_frontends
if /i "%~2"=="--clean-frontend" call :clean_target_frontends
if /i "%~3"=="--clean-frontend" call :clean_target_frontends
if /i "%~4"=="--clean-frontend" call :clean_target_frontends

(
    echo ===== deploy %TARGET% %DATE% %TIME% =====
    echo services=%SERVICES%
) > "%DEPLOY_LOG%"

call :step "Building %SERVICES% (quiet — details in log)"
call :info "Log file: %DEPLOY_LOG%"
docker compose build --quiet %SERVICES% >> "%DEPLOY_LOG%" 2>&1
if errorlevel 1 (
    call :fail_with_log "Image build failed for %SERVICES%."
    exit /b 1
)
call :ok "Images built"

call :step "Recreating %SERVICES%"
docker compose up -d --no-deps --quiet-pull %SERVICES% >> "%DEPLOY_LOG%" 2>&1
if errorlevel 1 (
    call :fail_with_log "Failed to start %SERVICES%."
    exit /b 1
)
call :ok "Containers updated"

if defined WAIT_CONTAINERS (
    for %%c in (%WAIT_CONTAINERS%) do (
        call :step "Waiting for %%c health"
        call :wait_named_health %%c 24
    )
)

call :banner "Partial deploy complete"
call :print_target_endpoints
echo.
call :print_status_table
echo.
exit /b 0

:clean_target_frontends
if not defined CLEAN_DIRS exit /b 0
for %%d in (%CLEAN_DIRS%) do call :clean_one_frontend "%%d"
exit /b 0

:print_target_endpoints
if /i "%TARGET%"=="pos" (
    echo   Retail POS  http://localhost:3003
    exit /b 0
)
if /i "%TARGET%"=="wms" (
    echo   WMS UI      http://localhost:3000
    exit /b 0
)
if /i "%TARGET%"=="admin" (
    echo   Admin UI    http://localhost:3002
    exit /b 0
)
if /i "%TARGET%"=="frontends" (
    echo   WMS UI      http://localhost:3000
    echo   Admin UI    http://localhost:3002
    echo   Retail POS  http://localhost:3003
    exit /b 0
)
if /i "%TARGET%"=="backend" (
    echo   WMS API     http://localhost:8080
    exit /b 0
)
if /i "%TARGET%"=="admin-api" (
    echo   Admin API   http://localhost:8081
    exit /b 0
)
if /i "%TARGET%"=="gateway" (
    echo   Gateway     http://localhost:8080  and  :8081
    exit /b 0
)
if /i "%TARGET%"=="apis" (
    echo   WMS API     http://localhost:8080
    echo   Admin API   http://localhost:8081
    exit /b 0
)
call :print_endpoints
exit /b 0

:print_endpoints
echo   Data plane ^(WMS^)
echo     UI        http://localhost:3000
echo     API       http://localhost:8080
echo     Swagger   http://localhost:8080/swagger-ui.html
echo   Retail POS ^(offline-first register^)
echo     UI        http://localhost:3003
echo     API       POST /api/v1/pos/sync-receipts via :8080
echo   Control plane ^(Super Admin^)
echo     UI        http://localhost:3002
echo     API       http://localhost:8081
echo     Host hint Host: admin.invsys.com on gateway :8081
echo   Observability / data
echo     Grafana   http://localhost:3001   ^(admin / admin^)
echo     Mailpit   http://localhost:8025   ^(SMTP 1025, demo tenant only^)
echo     Postgres  localhost:5432
echo     PgBouncer localhost:6432
exit /b 0

:print_status_table
echo   Containers
echo   ----------------------------------------------------------------------
for /f "usebackq tokens=1,2 delims=|" %%a in (`docker compose ps -a --format "{{.Name}}|{{.Status}}" 2^>nul`) do (
    set "CNAME=%%a"
    set "CSTAT=%%b"
    rem pad name roughly for alignment
    set "PAD=!CNAME!                              "
    set "PAD=!PAD:~0,22!"
    echo   !PAD! !CSTAT!
)
exit /b 0

:down
call :banner "InventorySystem undeploy"
call :require_docker
if errorlevel 1 exit /b 1
(
    echo ===== down %DATE% %TIME% =====
) > "%DEPLOY_LOG%"
call :undeploy
echo.
call :ok "Stack stopped. Database volume preserved (invsys_pgdata)."
call :info "Wipe DB volume: docker compose down -v"
echo.
exit /b 0

:status
call :banner "InventorySystem status"
call :require_docker
if errorlevel 1 exit /b 1
call :print_status_table
echo.
call :print_endpoints
echo.
exit /b 0

:clean_one_frontend
:: %~1 = frontend directory
set "FE=%~1"
if not exist "%FE%\package.json" (
    call :info "Skip clean ^(missing^): %FE%"
    exit /b 0
)
call :step "Cleaning %FE%"
if exist "%FE%\node_modules" (
    rmdir /s /q "%FE%\node_modules"
    call :ok "Removed node_modules"
)
if exist "%FE%\dist" (
    rmdir /s /q "%FE%\dist"
    call :ok "Removed dist"
)
if exist "%FE%\.vite" (
    rmdir /s /q "%FE%\.vite"
    call :ok "Removed .vite cache"
)
for %%f in (tsconfig.tsbuildinfo tsconfig.app.tsbuildinfo tsconfig.node.tsbuildinfo) do (
    if exist "%FE%\%%f" (
        del /f /q "%FE%\%%f"
        call :ok "Removed %%f"
    )
)
pushd "%FE%"
where npm >nul 2>&1
if not errorlevel 1 (
    call npm run clean --if-present >nul 2>&1
)
where pnpm >nul 2>&1
if not errorlevel 1 (
    call pnpm run clean --if-present >nul 2>&1
)
popd
exit /b 0

:clean_frontend
call :step "Cleaning frontend artifacts (WMS + admin + POS monorepo)"
if not exist "%WMS_FRONTEND%\package.json" if not exist "%ADMIN_FRONTEND%\package.json" (
    call :err "No frontend package.json found under frontends\apps. Run from repo root."
    exit /b 1
)
call :clean_one_frontend "%WMS_FRONTEND%"
call :clean_one_frontend "%ADMIN_FRONTEND%"
call :clean_one_frontend "%POS_FRONTEND%"
if exist "frontends\node_modules" (
    rmdir /s /q "frontends\node_modules"
    call :ok "Removed frontends\node_modules"
)
call :ok "Frontend clean complete"
exit /b 0

:seed
call :banner "Load demo seed"
call :require_docker
if errorlevel 1 exit /b 1
docker inspect invsys-db >nul 2>&1
if errorlevel 1 (
    call :err "invsys-db is not running. Run deploy.bat deploy first."
    exit /b 1
)

(
    echo ===== seed %DATE% %TIME% =====
) > "%DEPLOY_LOG%"

call :step "Applying ops/demo_seed.sql"
docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -q -f /seed/demo_seed.sql >> "%DEPLOY_LOG%" 2>&1
if errorlevel 1 (
    call :fail_with_log "Demo seed failed."
    exit /b 1
)
call :ok "Base demo seed applied"

if exist "ops\demo_seed_tenants_extra.sql" (
    call :step "Applying extra tenant seed"
    docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -q -f /seed/demo_seed_tenants_extra.sql >> "%DEPLOY_LOG%" 2>&1
    if errorlevel 1 (
        call :fail_with_log "Extra tenant seed failed."
        exit /b 1
    )
    call :ok "Extra tenants applied"
)

echo.
call :ok "Seed complete"
call :info "WMS login:   owner@demo.test / password123"
call :info "POS register: http://localhost:3003  ^(same tenant login; checkout is offline-first^)"
call :info "Admin login: owner@demo.test / password123  ^(platform_admins; UI :3002^)"
call :info "Floor PIN ^(after opening Fulfillment^): 1234"
call :info "Picker: picker@demo.test / password123"
echo.
exit /b 0

:help
echo.
echo InventorySystem deploy helper
echo.
echo Usage: deploy.bat [command] [options]
echo.
echo Commands:
echo   deploy                     Rebuild and start the full stack ^(quiet console^)
echo   deploy ^<target^>            Rebuild only one plane ^(does not stop the rest^)
echo   deploy --clean-frontend    Clean frontend artifacts, then deploy
echo   deploy --no-chatbot        Deploy without Support Co-Pilot ^(WMS api + web^)
echo   deploy --with-chatbot      Deploy with Support Co-Pilot ^(overrides marker^)
echo   --no-chatbot               Shorthand for: deploy --no-chatbot
echo   --with-chatbot             Shorthand for: deploy --with-chatbot
echo   --clean-frontend           Shorthand for: deploy --clean-frontend
echo.
echo Targets ^(also valid as the first argument, e.g. deploy.bat pos^):
echo   pos / frontend-pos         Retail POS UI  invsys-pos-web  :3003
echo   wms / frontend / web       WMS UI         invsys-web      :3000
echo   admin / frontend-admin     Admin UI       invsys-admin-web :3002
echo   frontends / ui             All three SPAs
echo   backend / api / wms-api    WMS API        invsys-api      :8080
echo   admin-api / backend-admin  Admin API      invsys-admin-api :8081
echo   gateway / api-gateway      Nginx gateway  :8080 / :8081
echo   apis / backends            WMS API + Admin API + gateway
echo   down                       Stop and remove containers ^(keeps DB volume^)
echo   undeploy                   Alias for down
echo   status                     Compact container status + URLs
echo   clean-frontend             Remove frontend_wms / frontend_admin / frontend_pos caches
echo   clean                      Alias for clean-frontend
echo   seed                       Load demo SQL ^(quiet; errors show log tail^)
echo   chatbot-enable             Persistently ENABLE chatbot for next deploys
echo   chatbot-disable            Persistently DISABLE chatbot for next deploys
echo   chatbot-status             Show whether chatbot is enabled or disabled
echo   help                       Show this help
echo.
echo Planes:
echo   Data plane     frontend_wms + invsys-app via gateway :8080
echo   Retail POS     frontend_pos + invsys-pos-api via gateway :8080 ^(:3003^)
echo   Control plane  frontend_admin + invsys-admin-api via gateway :8081
echo                  Tenants, billing, impersonation, RAG ingest, kill-switch,
echo                  audit, shards, DLQ, telemetry, compliance, reports
echo.
echo Chatbot toggle applies to WMS BACKEND and frontend_wms together:
echo   - Backend: omit invsys-chatbot jar ^(+ INVSYS_CHATBOT_ENABLED^)
echo   - Frontend: VITE_ENABLE_CHATBOT + stub bridge
echo   Preference file: .invsys-chatbot-disabled ^(repo root^)
echo.
echo On failure, the last 40 lines of .deploy-last.log are printed.
echo.
echo Examples:
echo   deploy.bat
echo   deploy.bat pos
echo   deploy.bat deploy wms
echo   deploy.bat deploy backend
echo   deploy.bat deploy apis
echo   deploy.bat deploy frontends
echo   deploy.bat --no-chatbot
echo   deploy.bat deploy --clean-frontend
echo   deploy.bat chatbot-disable
echo   deploy.bat deploy
echo   deploy.bat deploy --with-chatbot
echo   deploy.bat seed
echo.
exit /b 0
