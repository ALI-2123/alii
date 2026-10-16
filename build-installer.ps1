# Builds the Blade Client Windows installer (Blade-Client-Setup.exe) on your PC.
# It works on the drive with the most free space, so a nearly full C: drive does not stop it.
#
#   1. Build the mod jars first:  powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Mc all
#   2. Then run:                  powershell -ExecutionPolicy Bypass -File .\build-installer.ps1
#
# The installer is saved in the "dist" folder next to this file.
# Choose the drive yourself with:  powershell -ExecutionPolicy Bypass -File .\build-installer.ps1 -WorkDir D:\blade-installer

param(
    [string]$WorkDir = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot

function Fail($message) {
    Write-Host ""
    Write-Host $message -ForegroundColor Red
    exit 1
}

# 1. Node.js
$ErrorActionPreference = 'Continue'
$nodeVersion = cmd /c "node --version 2>&1" | Select-Object -First 1
$ErrorActionPreference = 'Stop'
if ($nodeVersion -notmatch '^v(1[89]|[2-9][0-9])') {
    Fail "You need Node.js 18 or newer. Install it with:  winget install OpenJS.NodeJS.LTS`nThen close this window, open a new PowerShell, and run this script again."
}

# 2. The mod jars should be in the bundled folder, otherwise Install Blade mod has nothing to install
$jars = @()
if (Test-Path (Join-Path $root 'bundled')) { $jars = @(Get-ChildItem (Join-Path $root 'bundled') -Filter 'blade-client-*.jar') }
if ($jars.Count -eq 0) {
    Write-Host "Warning: no Blade mod jars in the bundled folder. The installer will work, but Install Blade mod won't." -ForegroundColor Yellow
    Write-Host "         Run build-mod.ps1 -Mc all first (or copy the jars from GitHub) if you want the mod inside." -ForegroundColor Yellow
} else {
    Write-Host "Mod jars that go into the installer:"
    foreach ($j in $jars) { Write-Host "  $($j.Name)" }
}

# 3. A work folder on a drive with room
$neededGb = 4
if (-not $WorkDir) {
    $best = [System.IO.DriveInfo]::GetDrives() | Where-Object { $_.DriveType -eq 'Fixed' -and $_.IsReady } |
        Sort-Object AvailableFreeSpace -Descending | Select-Object -First 1
    if (-not $best) { Fail "Couldn't find a drive to build on. Use -WorkDir D:\blade-installer" }
    $WorkDir = Join-Path $best.RootDirectory.FullName 'blade-installer'
}
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$driveRoot = [System.IO.Path]::GetPathRoot((Resolve-Path $WorkDir).Path)
$freeGb = [math]::Round((New-Object System.IO.DriveInfo($driveRoot)).AvailableFreeSpace / 1GB, 1)
Write-Host "Building in $WorkDir ($freeGb GB free on $driveRoot)"
if ($freeGb -lt $neededGb) {
    Fail "Not enough disk space on $driveRoot. The installer build needs about $neededGb GB free.`nFree some space, or use another drive:`n    powershell -ExecutionPolicy Bypass -File .\build-installer.ps1 -WorkDir D:\blade-installer"
}

# 4. Copy the project (without the heavy folders) and keep every cache in the work folder
$app = Join-Path $WorkDir 'app'
if (Test-Path $app) { Remove-Item $app -Recurse -Force }
Write-Host "Copying the project..."
robocopy $root $app /E /XD node_modules dist .git .cache build .gradle server-bladesmp bladesmp-plugin mod docs /XF *.zip *.exe /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { Fail "Couldn't copy the project (robocopy code $LASTEXITCODE)." }

$env:npm_config_cache = Join-Path $WorkDir 'npm-cache'
$env:ELECTRON_CACHE = Join-Path $WorkDir 'electron-cache'
$env:ELECTRON_BUILDER_CACHE = Join-Path $WorkDir 'electron-builder-cache'
$env:CSC_IDENTITY_AUTO_DISCOVERY = 'false'

Push-Location $app
try {
    Write-Host "Installing packages (the first time this downloads a few hundred MB)..."
    $ErrorActionPreference = 'Continue'
    & npm install
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { Fail "npm install failed. Copy the red lines above and send them to Claude." }

    Write-Host "Building the installer..."
    $ErrorActionPreference = 'Continue'
    $output = & npm run dist 2>&1 | Tee-Object -Variable buildOutput | ForEach-Object { Write-Host $_; $_ }
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) {
        if (($buildOutput | Out-String) -match 'symbolic link') {
            Fail "Windows blocked the build from making a link. Turn on Developer Mode (Settings > System > For developers) or run PowerShell as administrator, then run this script again."
        }
        Fail "The installer build failed. Copy the red lines above (the first few matter most) and send them to Claude."
    }
} finally {
    Pop-Location
}

$exe = Join-Path $app 'dist\Blade-Client-Setup.exe'
if (-not (Test-Path $exe)) { Fail "The build finished but Blade-Client-Setup.exe was not found in $app\dist." }
$out = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Copy-Item $exe (Join-Path $out 'Blade-Client-Setup.exe') -Force
$sizeMb = [math]::Round((Get-Item (Join-Path $out 'Blade-Client-Setup.exe')).Length / 1MB, 1)
Write-Host ""
Write-Host "Done: $out\Blade-Client-Setup.exe ($sizeMb MB)" -ForegroundColor Green
Write-Host "Put this file in a GitHub Release (see README, Publish the installer) so the website can download it."
