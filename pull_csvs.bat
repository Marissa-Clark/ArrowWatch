@echo off
setlocal enabledelayedexpansion

set ADB=C:\Users\maris\AppData\Local\Android\Sdk\platform-tools\adb.exe
set PHONE=38271FDJG00DGY
set WATCH_IP=192.168.1.108
set APP_PATH=/sdcard/Android/data/com.archery.wear/files/Archery

:: Output folder = script directory\pulled_csvs\
set OUT=%~dp0pulled_csvs
mkdir "%OUT%\phone" 2>nul
mkdir "%OUT%\watch"  2>nul

echo ============================================
echo   Archery CSV Puller
echo ============================================
echo.

:: ── Phone ────────────────────────────────────
echo [1/2] Pulling from phone (%PHONE%)...
"%ADB%" -s %PHONE% pull %APP_PATH% "%OUT%\phone"
if %errorlevel% neq 0 (
    echo   WARNING: phone pull failed – is USB connected?
)
echo.

:: ── Watch (port as arg or prompted) ──────────
if "%1"=="" (
    set /p WATCH_PORT="Enter watch ADB port (e.g. 44201): "
) else (
    set WATCH_PORT=%1
)

echo [2/2] Connecting to watch at %WATCH_IP%:!WATCH_PORT!...
"%ADB%" connect %WATCH_IP%:!WATCH_PORT!
echo Pulling from watch...
"%ADB%" -s %WATCH_IP%:!WATCH_PORT! pull %APP_PATH% "%OUT%\watch"
if %errorlevel% neq 0 (
    echo   WARNING: watch pull failed – check port / WiFi ADB
)
echo.

echo Done!  Files saved to:
echo   %OUT%
echo.
explorer "%OUT%"

endlocal
pause
