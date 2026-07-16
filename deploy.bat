@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=deploy"

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

:require_docker
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running. Start Docker Desktop and try again.
    exit /b 1
)
exit /b 0

:undeploy
echo.
echo === Undeploying existing InventorySystem stack ===
for %%c in (invsys-web invsys-api invsys-db invsys-minio invsys-minio-init) do (
    docker inspect %%c >nul 2>&1
    if not errorlevel 1 (
        echo   Stopping and removing container %%c ...
        docker stop %%c >nul 2>&1
        docker rm %%c >nul 2>&1
    )
)
docker compose down --remove-orphans >nul 2>&1
echo   Stack removed.
exit /b 0

exit /b 0

:ensure_jwt_keys
if exist "ops\jwt\dev-private.pem" if exist "ops\jwt\dev-public.pem" exit /b 0
echo.
echo === Generating persistent dev JWT keys ===
if not exist "ops\jwt" mkdir "ops\jwt"
pushd backend
call mvn -q -Dtest=JwtKeyExportTest test
set "KEYGEN_ERR=!errorlevel!"
popd
if !KEYGEN_ERR! neq 0 (
    echo [ERROR] Failed to generate JWT keys.
    exit /b 1
)
echo   JWT keys written to ops\jwt\
exit /b 0

:deploy
call :require_docker
if errorlevel 1 exit /b 1

if /i "%~2"=="--clean-frontend" (
    call :clean_frontend
    if errorlevel 1 exit /b 1
)

call :ensure_jwt_keys
if errorlevel 1 exit /b 1

call :undeploy

echo.
echo === Building and deploying db, backend, and frontend ===
docker compose up --build -d
if errorlevel 1 (
    echo [ERROR] Deploy failed.
    exit /b 1
)

echo.
echo === Waiting for services to become healthy ===
set /a RETRIES=30
:wait_health
docker inspect --format="{{.State.Health.Status}}" invsys-api 2>nul | findstr /i "healthy" >nul
if not errorlevel 1 goto :deploy_done
set /a RETRIES-=1
if !RETRIES! leq 0 (
    echo [WARN] Backend health check timed out. Check logs: docker compose logs backend
    goto :deploy_done
)
timeout /t 5 /nobreak >nul
goto :wait_health

:deploy_done
echo.
echo === Deploy complete ===
echo   Frontend : http://localhost:3000
echo   API      : http://localhost:8080
echo   Swagger  : http://localhost:8080/swagger-ui.html
echo   Grafana  : http://localhost:3001  (admin / admin)
echo   Postgres : localhost:5432
echo   PgBouncer: localhost:6432  (transaction pool)
echo.
echo   Load demo data: deploy.bat seed
echo   Check status  : deploy.bat status
exit /b 0

:down
call :require_docker
if errorlevel 1 exit /b 1
call :undeploy
echo.
echo Stack stopped and removed. Database volume preserved (invsys_pgdata).
echo To wipe DB volume: docker compose down -v
exit /b 0

:status
call :require_docker
if errorlevel 1 exit /b 1
echo.
echo === Container status ===
docker compose ps -a
exit /b 0

:clean_frontend
echo.
echo === Cleaning frontend project ===
set "FRONTEND=frontend"
if not exist "%FRONTEND%\package.json" (
    echo [ERROR] frontend\package.json not found. Run from repo root.
    exit /b 1
)

if exist "%FRONTEND%\node_modules" (
    echo   Removing node_modules ...
    rmdir /s /q "%FRONTEND%\node_modules"
)
if exist "%FRONTEND%\dist" (
    echo   Removing dist ...
    rmdir /s /q "%FRONTEND%\dist"
)
if exist "%FRONTEND%\.vite" (
    echo   Removing .vite cache ...
    rmdir /s /q "%FRONTEND%\.vite"
)
for %%f in (tsconfig.tsbuildinfo tsconfig.app.tsbuildinfo tsconfig.node.tsbuildinfo) do (
    if exist "%FRONTEND%\%%f" (
        echo   Removing %%f ...
        del /f /q "%FRONTEND%\%%f"
    )
)

pushd "%FRONTEND%"
where npm >nul 2>&1
if not errorlevel 1 (
    call npm run clean --if-present >nul 2>&1
)
popd

echo   Frontend clean complete.
exit /b 0

:seed
call :require_docker
if errorlevel 1 exit /b 1
docker inspect invsys-db >nul 2>&1
if errorlevel 1 (
    echo [ERROR] invsys-db is not running. Run deploy.bat deploy first.
    exit /b 1
)
echo.
echo === Loading demo seed data ===
docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -f /seed/demo_seed.sql
if errorlevel 1 (
    echo [ERROR] Demo seed failed.
    exit /b 1
)
if exist "ops\demo_seed_tenants_extra.sql" (
    echo   Loading extra tenant seed ...
    docker compose exec -T db psql -U app_owner -d invsys -v ON_ERROR_STOP=1 -f /seed/demo_seed_tenants_extra.sql
    if errorlevel 1 (
        echo [ERROR] Extra tenant seed failed.
        exit /b 1
    )
)
echo   Demo seed applied. Login: demo-corp / owner@demo.test / password123
exit /b 0

:help
echo.
echo InventorySystem deploy helper
echo.
echo Usage: deploy.bat [command] [options]
echo.
echo Commands:
echo   deploy              Stop existing stack, rebuild, and start db + backend + frontend
echo   deploy --clean-frontend   Clean frontend artifacts before deploy
echo   down                Stop and remove running containers (keeps DB volume)
echo   undeploy            Alias for down
echo   status              Show container status
echo   clean-frontend      Remove frontend node_modules, dist, and build cache
echo   clean               Alias for clean-frontend
echo   seed                Load ops/demo_seed.sql into the database
echo   help                Show this help
echo.
echo Examples:
echo   deploy.bat
echo   deploy.bat deploy --clean-frontend
echo   deploy.bat clean-frontend
echo   deploy.bat seed
exit /b 0
