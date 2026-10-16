# Builds the Blade in-game mod (Minecraft 1.21.11, 1.21.4 or 1.21.1) or the BladeSMP server plugin.
# You do NOT need to install Gradle. Run from this folder (the one that contains "mod" and "bundled"):
#
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1                       builds the mod for 1.21.11
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Mc 1.21.4            builds the mod for 1.21.4
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Mc all               builds 1.21.11, 1.21.4 and 1.21.1
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Target plugin        builds the BladeSMP server plugin
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -Target all           builds everything
#
# Big downloads go on the drive with the most free space. To choose a drive yourself:
#   powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -WorkDir D:\blade-build

param(
    [ValidateSet('mod', 'plugin', 'all')]
    [string]$Target = 'mod',
    [string]$Mc = '1.21.11',
    [string]$Server = '1.21.11',
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

$modDir = Join-Path $root 'mod'
$pluginDir = Join-Path $root 'bladesmp-plugin'
$doMod = ($Target -eq 'mod' -or $Target -eq 'all')
$doPlugin = ($Target -eq 'plugin' -or $Target -eq 'all')

$mcList = @()
if ($doMod) {
    if (-not (Test-Path (Join-Path $modDir 'build.gradle'))) { Fail "Can't find the mod folder. Put this file next to the 'mod' folder and run it again." }
    if ($Mc -eq 'all') { $mcList = @('1.21.11', '1.21.4', '1.21.1') } else { $mcList = @($Mc -split '[ ,]+' | Where-Object { $_ }) }
    foreach ($v in $mcList) {
        if (-not (Test-Path (Join-Path $modDir "versions\$v.properties"))) {
            Fail "Minecraft $v is not supported. Use 1.21.11, 1.21.4 or 1.21.1 (or -Mc all)."
        }
    }
}
if ($doPlugin -and -not (Test-Path (Join-Path $pluginDir 'build.gradle'))) {
    Fail "Can't find the bladesmp-plugin folder. Put this file next to it and run it again."
}

# 1. Java 21 JDK (the compiler, not just the runtime)
$ErrorActionPreference = 'Continue'
$javac = cmd /c "javac -version 2>&1" | Select-Object -First 1
$ErrorActionPreference = 'Stop'
if ($javac -notmatch 'javac (2[1-9]|[3-9][0-9])') {
    Fail "You need the Java 21 JDK. Install it with:  winget install EclipseAdoptium.Temurin.21.JDK`nThen close this window, open a new PowerShell, and run this script again."
}

# 2. Pick a work folder on a drive with enough room
$neededGb = 2
if ($doMod) { $neededGb = 6 + 3 * ([math]::Max(1, $mcList.Count) - 1) }
if ($doPlugin -and -not $doMod) { $neededGb = 2 }
if (-not $WorkDir) {
    $best = [System.IO.DriveInfo]::GetDrives() | Where-Object { $_.DriveType -eq 'Fixed' -and $_.IsReady } |
        Sort-Object AvailableFreeSpace -Descending | Select-Object -First 1
    if (-not $best) { Fail "Couldn't find a drive to build on. Use -WorkDir D:\blade-build" }
    $WorkDir = Join-Path $best.RootDirectory.FullName 'blade-gradle'
}
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$driveRoot = [System.IO.Path]::GetPathRoot((Resolve-Path $WorkDir).Path)
$freeGb = [math]::Round((New-Object System.IO.DriveInfo($driveRoot)).AvailableFreeSpace / 1GB, 1)
Write-Host "Building in $WorkDir ($freeGb GB free on $driveRoot)"
if ($freeGb -lt $neededGb) {
    Fail "Not enough disk space on $driveRoot. This build needs about $neededGb GB free.`nFree some space (Settings > System > Storage), or build on another drive, for example:`n    powershell -ExecutionPolicy Bypass -File .\build-mod.ps1 -WorkDir D:\blade-build"
}

# Gradle's downloads (including Minecraft files) go in the work folder too
$env:GRADLE_USER_HOME = Join-Path $WorkDir 'gradle-home'

# 3. Gradle (downloaded once)
$gradleVersion = '8.14'
$gradleDir = Join-Path $WorkDir "gradle-$gradleVersion"
$gradleBat = Join-Path $gradleDir 'bin\gradle.bat'
if (-not (Test-Path $gradleBat)) {
    if (Test-Path $gradleDir) { Remove-Item $gradleDir -Recurse -Force }   # leftovers from a failed unpack
    $zip = Join-Path $WorkDir "gradle-$gradleVersion-bin.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Write-Host "Downloading Gradle $gradleVersion (about 130 MB, one time only)..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
    Write-Host "Unpacking..."
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $WorkDir)
    } catch {
        if (Test-Path $gradleDir) { Remove-Item $gradleDir -Recurse -Force -ErrorAction SilentlyContinue }
        Fail "Couldn't unpack Gradle: $($_.Exception.Message)"
    } finally {
        if (Test-Path $zip) { Remove-Item $zip -Force -ErrorAction SilentlyContinue }
    }
    if (-not (Test-Path $gradleBat)) { Fail "Gradle unpacked but gradle.bat is missing. Delete $WorkDir and try again." }
}

function Run-Gradle($folder, $gradleArgs) {
    Push-Location $folder
    $ErrorActionPreference = 'Continue'
    & $gradleBat build @gradleArgs --no-daemon
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Pop-Location
    if ($code -ne 0) {
        Fail "The build failed. Copy the red error lines above (the first few matter most) and send them to Claude."
    }
}

$done = @()

# 4a. The mod, once per Minecraft version
if ($doMod) {
    $dest = Join-Path $root 'bundled'
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    foreach ($v in $mcList) {
        Write-Host ""
        Write-Host "Building the Blade mod for Minecraft $v. The first build downloads files, so give it a few minutes..."
        Run-Gradle $modDir @("-Pmc=$v")
        $jar = Get-ChildItem (Join-Path $modDir 'build\libs') -Filter "blade-client-*+$v.jar" |
            Where-Object { $_.Name -notmatch 'sources|dev' } | Sort-Object LastWriteTime | Select-Object -Last 1
        if (-not $jar) { Fail "The build finished but no jar for $v was found in $modDir\build\libs." }
        # remove older Blade jars for this version so the launcher can never pick a stale one
        Get-ChildItem $dest -Filter "blade-client-*+$v.jar" | Where-Object { $_.Name -ne $jar.Name } | Remove-Item -Force
        Copy-Item $jar.FullName (Join-Path $dest $jar.Name) -Force
        $done += "$($jar.Name) -> $dest"
    }
}

# 4b. The BladeSMP plugin
if ($doPlugin) {
    Write-Host ""
    Write-Host "Building the BladeSMP plugin for Paper $Server..."
    Run-Gradle $pluginDir @("-Pserver=$Server")
    $jar = Get-ChildItem (Join-Path $pluginDir 'build\libs') -Filter 'BladeSMP.jar' | Select-Object -First 1
    if (-not $jar) { Fail "The build finished but BladeSMP.jar was not found in $pluginDir\build\libs." }
    $dest = Join-Path $root 'server-bladesmp\plugins'
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item $jar.FullName (Join-Path $dest $jar.Name) -Force
    $done += "$($jar.Name) -> $dest"
}

Write-Host ""
foreach ($line in $done) { Write-Host "Done: $line" -ForegroundColor Green }
if ($doMod) { Write-Host "Next: open the launcher, pick the same Minecraft version, and press Install Blade mod on the Mods page." }
if ($doPlugin) { Write-Host "Next: run server-bladesmp\setup-server.ps1 to set up the server." }
