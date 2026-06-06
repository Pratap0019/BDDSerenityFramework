@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%Test-AtlassianConnections.ps1"
set "NO_PAUSE="

if /I "%~1"=="--no-pause" (
    set "NO_PAUSE=1"
    shift
)

if not exist "%PS_SCRIPT%" (
    echo PowerShell script not found: "%PS_SCRIPT%"
    exit /b 1
)

if "%~1"=="" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"
) else if "%~2"=="" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" -ConfigPath "%~1"
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" -ConfigPath "%~1" -TimeoutSec %~2
)

set "EXIT_CODE=%ERRORLEVEL%"

if not defined NO_PAUSE (
    echo.
    echo Press any key to close this window...
    pause >nul
)

exit /b %EXIT_CODE%

