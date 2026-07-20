@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=deploy"
set "DEPLOY_LOG=%~dp0.deploy-last.log"
set "COMPOSE_ANSI=never"
set "DOCKER_CLI_HINTS=false"

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
for %%c in (invsys-web invsys-api invsys-db invsys-minio invsys-minio-init) do (
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
call mvn -q -Dtest=JwtKeyExportTest test >> "%DEPLOY_LOG%" 2>&1
set "KEYGEN_ERR=!errorlevel!"
popd
if !KEYGEN_ERR! neq 0 (
    call :fail_with_log "Failed to generate JWT keys."
    exit /b 1
)
call :ok "JWT keys written to ops\jwt\"
exit /b 0

:deploy
call :banner "InventorySystem deploy"
call :require_docker
if errorlevel 1 exit /b 1
call :ok "Docker is available"

if /i "%~2"=="--clean-frontend" (
    call :clean_frontend
    if errorlevel 1 exit /b 1
)

(
    echo ===== deploy %DATE% %TIME% =====
) > "%DEPLOY_LOG%"

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

call :step "Waiting for API health"
set /a RETRIES=36
set "API_HEALTH=unknown"
:wait_health
set "API_HEALTH=unknown"
for /f "usebackq delims=" %%s in (`docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" invsys-api 2^>nul`) do set "API_HEALTH=%%s"
if /i "!API_HEALTH!"=="healthy" (
    call :ok "invsys-api is healthy"
    goto :deploy_summary
)
echo     ... invsys-api: !API_HEALTH!  ^(!RETRIES! checks left^)
set /a RETRIES-=1
if !RETRIES! leq 0 (
    call :warn "API health timed out — check: docker compose logs api --tail 80"
    goto :deploy_summary
)
timeout /t 5 /nobreak >nul
goto :wait_health

:deploy_summary
call :banner "Deploy complete"
call :print_endpoints
echo.
call :print_status_table
echo.
call :info "Next:  deploy.bat seed     ^(demo users / password123^)"
call :info "       deploy.bat status"
echo.
exit /b 0

:print_endpoints
echo   Frontend  http://localhost:3000
echo   API       http://localhost:8080
echo   Swagger   http://localhost:8080/swagger-ui.html
echo   Grafana   http://localhost:3001   ^(admin / admin^)
echo   Postgres  localhost:5432
echo   PgBouncer localhost:6432
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

:clean_frontend
call :step "Cleaning frontend artifacts"
set "FRONTEND=frontend"
if not exist "%FRONTEND%\package.json" (
    call :err "frontend\package.json not found. Run from repo root."
    exit /b 1
)

if exist "%FRONTEND%\node_modules" (
    rmdir /s /q "%FRONTEND%\node_modules"
    call :ok "Removed node_modules"
)
if exist "%FRONTEND%\dist" (
    rmdir /s /q "%FRONTEND%\dist"
    call :ok "Removed dist"
)
if exist "%FRONTEND%\.vite" (
    rmdir /s /q "%FRONTEND%\.vite"
    call :ok "Removed .vite cache"
)
for %%f in (tsconfig.tsbuildinfo tsconfig.app.tsbuildinfo tsconfig.node.tsbuildinfo) do (
    if exist "%FRONTEND%\%%f" (
        del /f /q "%FRONTEND%\%%f"
        call :ok "Removed %%f"
    )
)

pushd "%FRONTEND%"
where npm >nul 2>&1
if not errorlevel 1 (
    call npm run clean --if-present >nul 2>&1
)
popd

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
call :info "Login: owner@demo.test / password123"
call :info "Floor PIN (after opening Fulfillment): 1234"
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
echo   deploy                     Rebuild and start the stack ^(quiet console^)
echo   deploy --clean-frontend    Clean frontend artifacts, then deploy
echo   down                       Stop and remove containers ^(keeps DB volume^)
echo   undeploy                   Alias for down
echo   status                     Compact container status + URLs
echo   clean-frontend             Remove frontend node_modules / dist / cache
echo   clean                      Alias for clean-frontend
echo   seed                       Load demo SQL ^(quiet; errors show log tail^)
echo   help                       Show this help
echo.
echo On failure, the last 40 lines of .deploy-last.log are printed.
echo.
echo Examples:
echo   deploy.bat
echo   deploy.bat deploy --clean-frontend
echo   deploy.bat seed
echo.
exit /b 0
