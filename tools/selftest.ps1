# Runs the dev client in self-test mode and waits for it to finish.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\selftest.ps1 [-Mode safe|early|menu] [-TimeoutSec 900]
#
# What it does:
#   1. Backs up run\options.txt and run\config\autoomen.json.
#   2. Starts `gradlew runSelftest` (dev client with -Dao.selftest=true) hidden in the background.
#      Loom 1.18 needs Gradle itself to run on Java 25, but ~/.gradle/gradle.properties pins
#      org.gradle.java.home to Java 21. A -Dorg.gradle.java.home=... on the command line wins over it.
#   3. Polls run\logs\latest.log until "[SelfTest] DONE", a crash, or the timeout.
#   4. Makes sure the game process is gone (only java processes started with -Dao.selftest=true are touched).
#   5. Restores the backups and prints the [SelfTest] log lines and the screenshot list.
#
# Exit code 0 = self-test finished with no failed step. Anything else = failure.

param(
    [ValidateSet('safe', 'early', 'menu')]
    [string]$Mode = 'safe',
    [int]$TimeoutSec = 900
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root 'run'
$logFile = Join-Path $runDir 'logs\latest.log'
$shotDir = Join-Path $runDir 'screenshots'
$outDir = Join-Path $root 'build\selftest'
$backupDir = Join-Path $outDir 'backup'
$gradleLog = Join-Path $outDir 'gradle.log'
$gradleErr = Join-Path $outDir 'gradle.err.log'

$jdk25 = $env:AO_JDK25
if (-not $jdk25) { $jdk25 = Join-Path $env:USERPROFILE 'scoop\apps\temurin25-jdk\current' }
$jdk25 = $jdk25 -replace '\\', '/'

function Get-SelfTestGames {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine -match 'ao\.selftest=true' }
}

function Stop-SelfTestGames {
    foreach ($p in @(Get-SelfTestGames)) {
        Write-Host "[harness] killing self-test game process $($p.ProcessId)"
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Read-LogText {
    if (-not (Test-Path $logFile)) { return '' }
    try {
        $fs = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
        try {
            $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
            return $sr.ReadToEnd()
        } finally { $fs.Dispose() }
    } catch { return '' }
}

New-Item -ItemType Directory -Force $outDir | Out-Null
if (Test-Path $backupDir) { Remove-Item -Recurse -Force $backupDir }
New-Item -ItemType Directory -Force $backupDir | Out-Null

Stop-SelfTestGames

$backups = @()
foreach ($rel in @('options.txt', 'config\autoomen.json')) {
    $src = Join-Path $runDir $rel
    if (Test-Path $src) {
        $dst = Join-Path $backupDir ($rel -replace '[\\/]', '__')
        Copy-Item $src $dst -Force
        $backups += [pscustomobject]@{ Source = $src; Backup = $dst }
    }
}

if (Test-Path $shotDir) { Get-ChildItem $shotDir -Filter 'selftest_*.png' | Remove-Item -Force }

# The mode is handed to the game through a file next to the run folder (the Loom run config has fixed JVM args).
New-Item -ItemType Directory -Force $runDir | Out-Null
Set-Content -Path (Join-Path $runDir 'selftest_mode.txt') -Value $Mode -Encoding ascii

$start = Get-Date
$gradleArgs = @('runSelftest', '--console=plain', "-Dorg.gradle.java.home=$jdk25")
$proc = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList $gradleArgs `
    -WorkingDirectory $root -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $gradleLog -RedirectStandardError $gradleErr

$result = 'TIMEOUT'
while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSec) {
    Start-Sleep -Seconds 2
    $fresh = (Test-Path $logFile) -and ((Get-Item $logFile).LastWriteTime -ge $start)
    $text = ''
    if ($fresh) { $text = Read-LogText }
    if ($text -match '\[SelfTest\] DONE') { $result = 'DONE'; break }
    if ($text -match '---- Minecraft Crash Report ----|Crash report saved to|Minecraft has crashed') { $result = 'CRASH'; break }
    if ($proc.HasExited) {
        Start-Sleep -Seconds 1
        if ($fresh) { $text = Read-LogText }
        if ($text -match '\[SelfTest\] DONE') { $result = 'DONE' } else { $result = 'EXITED_EARLY' }
        break
    }
}

# Give a clean shutdown a moment, then make sure nothing is left behind.
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline -and @(Get-SelfTestGames).Count -gt 0 -and $result -eq 'DONE') { Start-Sleep -Seconds 1 }
Stop-SelfTestGames
$deadline = (Get-Date).AddSeconds(20)
while ((Get-Date) -lt $deadline -and -not $proc.HasExited) { Start-Sleep -Seconds 1 }
if (-not $proc.HasExited) {
    # The wrapper is only waiting on a game that is already gone; stop it and its launcher JVM.
    & taskkill /PID $proc.Id /T /F | Out-Null
}
Start-Sleep -Seconds 1
$left = @(Get-SelfTestGames).Count

foreach ($b in $backups) { Copy-Item $b.Backup $b.Source -Force }
Remove-Item (Join-Path $runDir 'selftest_mode.txt') -Force -ErrorAction SilentlyContinue

$text = Read-LogText
$lines = @($text -split "`r?`n" | Where-Object { $_ -match '\[SelfTest\]' })
$failed = @($lines | Where-Object { $_ -match '\[SelfTest\] FAIL' }).Count

Write-Host "===== SELFTEST RESULT ($Mode): $result  (elapsed $([int]((Get-Date) - $start).TotalSeconds)s, failed steps: $failed, game processes left: $left) ====="
$lines | ForEach-Object { Write-Host ($_ -replace '^\[[^\]]*\] \[[^\]]*\] \([^)]*\) ', '') }
Write-Host '===== SCREENSHOTS ====='
if (Test-Path $shotDir) { Get-ChildItem $shotDir -Filter 'selftest_*.png' | Sort-Object Name | ForEach-Object { Write-Host "$($_.FullName)  $([int]($_.Length / 1KB)) KB" } }
if ($result -ne 'DONE') {
    Write-Host '===== LAST LOG LINES ====='
    @($text -split "`r?`n") | Select-Object -Last 40 | ForEach-Object { Write-Host $_ }
    Write-Host '===== GRADLE OUTPUT (tail) ====='
    if (Test-Path $gradleLog) { Get-Content $gradleLog -Tail 25 | ForEach-Object { Write-Host $_ } }
    if (Test-Path $gradleErr) { Get-Content $gradleErr -Tail 25 | ForEach-Object { Write-Host $_ } }
}

if ($result -eq 'DONE' -and $failed -eq 0 -and $left -eq 0) { exit 0 } else { exit 1 }
