@echo off
setlocal enabledelayedexpansion

set ADB=C:\Users\maris\AppData\Local\Android\Sdk\platform-tools\adb.exe
set PHONE=38271FDJG00DGY
set APP_PATH=/sdcard/Android/data/com.archery.wear/files/Archery

:: Timestamped output folder so pulls don't clobber each other
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set DT=%%I
set STAMP=%DT:~0,4%-%DT:~4,2%-%DT:~6,2%_%DT:~8,2%-%DT:~10,2%
set OUT=%~dp0pulled_csvs\phone_%STAMP%

mkdir "%OUT%" 2>nul

echo ============================================
echo   Archery CSV Puller  ^|  Phone only
echo ============================================
echo.

:: Check phone is visible
"%ADB%" -s %PHONE% get-state >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Phone %PHONE% not found.
    echo Make sure USB is connected and ADB is authorised.
    pause
    exit /b 1
)

echo Pulling CSVs from phone...
"%ADB%" -s %PHONE% pull "%APP_PATH%" "%OUT%"
if %errorlevel% neq 0 (
    echo WARNING: pull returned an error. Check USB connection / permissions.
) else (
    echo Done!
)

echo.
echo Files saved to:
echo   %OUT%
echo.
explorer "%OUT%"

endlocal
pause
