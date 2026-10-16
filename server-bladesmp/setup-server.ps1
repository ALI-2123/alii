# Sets up a BladeSMP server in this folder: Paper (1.21.11 by default), the BladeSMP plugin, and a start script.
# Run from this folder:  powershell -ExecutionPolicy Bypass -File .\setup-server.ps1
# Another version:       powershell -ExecutionPolicy Bypass -File .\setup-server.ps1 -Version 1.21.4
# Build the plugin first: powershell -ExecutionPolicy Bypass -File ..\build-mod.ps1 -Target plugin

param(
    [string]$Version = '1.21.11'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$here = $PSScriptRoot

function Fail($message) {
    Write-Host ""
    Write-Host $message -ForegroundColor Red
    exit 1
}

$ErrorActionPreference = 'Continue'
$java = cmd /c "java -version 2>&1" | Select-Object -First 1
$ErrorActionPreference = 'Stop'
if ($java -notmatch '"(2[1-9]|[3-9][0-9])') {
    Fail "The server needs Java 21 or newer. Install it with:  winget install EclipseAdoptium.Temurin.21.JRE`nThen open a new PowerShell and run this again."
}

if (-not (Test-Path (Join-Path $here 'plugins\BladeSMP.jar'))) {
    Fail "plugins\BladeSMP.jar is missing. Build it first:`n    powershell -ExecutionPolicy Bypass -File ..\build-mod.ps1 -Target plugin`nOr copy BladeSMP.jar from the BladeSMP server kit into the plugins folder."
}

$paper = Join-Path $here 'paper.jar'
if (-not (Test-Path $paper)) {
    Write-Host "Finding the latest Paper $Version..."
    try {
        # PaperMC moved to the Fill service. The old api.papermc.io/v2 address stopped getting builds on 31 Dec 2025.
        $headers = @{ 'User-Agent' = 'BladeSMP-setup/1.1' }
        $builds = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds" -Headers $headers
        $pick = $builds | Where-Object { $_.channel -eq 'STABLE' } | Select-Object -First 1
        if (-not $pick) { $pick = $builds | Select-Object -First 1 }
        if (-not $pick) { throw "No Paper build is listed for $Version yet." }
        $url = $pick.downloads.'server:default'.url
        if (-not $url) { throw "The build has no download link." }
        Write-Host "Downloading Paper $Version build $($pick.id) ($($pick.channel))..."
        Invoke-WebRequest -Uri $url -Headers $headers -OutFile $paper
    } catch {
        if (Test-Path $paper) { Remove-Item $paper -Force }
        Fail "Couldn't download Paper automatically ($($_.Exception.Message)).`nDownload Paper $Version from https://papermc.io/downloads/paper, save it in this folder as paper.jar, and run this again."
    }
}

if (-not (Test-Path (Join-Path $here 'eula.txt'))) {
    Write-Host ""
    Write-Host "Minecraft servers need you to accept Mojang's EULA: https://aka.ms/MinecraftEULA"
    $answer = Read-Host "Type YES to accept"
    if ($answer -ne 'YES') { Fail "The server can't run without accepting the EULA." }
    Set-Content (Join-Path $here 'eula.txt') "eula=true"
}

if (-not (Test-Path (Join-Path $here 'server.properties'))) {
    Set-Content (Join-Path $here 'server.properties') @(
        'motd=BladeSMP',
        'pvp=true',
        'online-mode=true',
        'difficulty=hard',
        'view-distance=10'
    )
}

Set-Content (Join-Path $here 'start.bat') @(
    '@echo off',
    'java -Xms2G -Xmx4G -jar paper.jar --nogui',
    'pause'
)

Write-Host ""
Write-Host "Done. Start the server with start.bat" -ForegroundColor Green
Write-Host "Players must use Minecraft $Version (or a version your server allows through ViaVersion)."
Write-Host "Then, in the launcher's Client page, set the BladeSMP address (localhost:25565 on this computer) and press Join BladeSMP."
