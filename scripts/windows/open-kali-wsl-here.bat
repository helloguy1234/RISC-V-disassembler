@echo off
setlocal

set "WIN_DIR=%CD%"
set "DISTRO="
set "WSL_DIR="
set "WSL_CMD=%*"

call :try_distro kali-linux
if not defined DISTRO call :try_distro kali

if not defined DISTRO (
    echo [ERROR] Khong mo duoc WSL distro Kali.
    echo Thu thu cong mot trong cac lenh sau:
    echo   wsl -d kali-linux
    echo   wsl -d kali
    exit /b 1
)

for /f "usebackq delims=" %%P in (`wsl.exe -d %DISTRO% wslpath "%WIN_DIR%" 2^>nul`) do (
    set "WSL_DIR=%%P"
)

if not defined WSL_DIR (
    echo [ERROR] Khong doi duoc duong dan Windows sang duong dan WSL.
    echo Thu muc hien tai: %WIN_DIR%
    exit /b 1
)

where wt.exe >nul 2>nul
if %errorlevel%==0 (
    if defined WSL_CMD (
        start "" wt.exe wsl.exe -d %DISTRO% --cd "%WSL_DIR%" bash -lc "%WSL_CMD%; exec bash"
    ) else (
        start "" wt.exe wsl.exe -d %DISTRO% --cd "%WSL_DIR%"
    )
    exit /b 0
)

if defined WSL_CMD (
    start "" wsl.exe -d %DISTRO% --cd "%WSL_DIR%" bash -lc "%WSL_CMD%; exec bash"
) else (
    start "" wsl.exe -d %DISTRO% --cd "%WSL_DIR%"
)
exit /b 0

:try_distro
wsl.exe -d %~1 --cd / /bin/true >nul 2>nul
if not errorlevel 1 set "DISTRO=%~1"
exit /b 0
