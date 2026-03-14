# install-watch.ps1
# Run this, THEN enable Debug over WiFi on the watch.
# It will auto-detect the watch and install the APK.

$ADB  = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$APK  = "$PSScriptRoot\wear\build\outputs\apk\debug\wear-debug.apk"
$IP   = "192.168.1.108"

# Ask for port up front so you can have it ready
$port = Read-Host "Type the port shown on watch (Developer Options -> Debug over WiFi), then press Enter"
$target = "${IP}:${port}"

Write-Host "`nWaiting for watch on $target — enable Debug over WiFi NOW..." -ForegroundColor Yellow

& $ADB disconnect 2>&1 | Out-Null

$connected = $false
for ($i = 1; $i -le 20; $i++) {
    $r = (& $ADB connect $target 2>&1) -join ""
    if ($r -match "connected to $target") {
        $connected = $true
        Write-Host "Connected!" -ForegroundColor Green
        break
    }
    Write-Host "  [$i/20] $r"
    Start-Sleep -Milliseconds 500
}

if (-not $connected) {
    Write-Host "`nFailed to connect. Re-enable Debug over WiFi and run the script again." -ForegroundColor Red
    pause; exit 1
}

Write-Host "`nInstalling APK..." -ForegroundColor Cyan
$out = (& $ADB -s $target install -r $APK 2>&1) -join "`n"
Write-Host $out

if ($out -match "Success") {
    Write-Host "`nLaunching on watch..." -ForegroundColor Cyan
    & $ADB -s $target shell am start -n "com.archery.wear/.presentation.MainActivity" 2>&1 | Out-Null
    Write-Host "Done! App is starting on the watch." -ForegroundColor Green
} else {
    Write-Host "Install failed — see output above." -ForegroundColor Red
}

pause
