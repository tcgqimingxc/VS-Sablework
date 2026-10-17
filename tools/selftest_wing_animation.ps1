# Physical-bearing wing state-machine self-test (no game launch, no JUnit/Gradle).
#
# Compiles the production class WingAnimation.java (pure Java, no Minecraft deps)
# together with tools/WingAnimationSelfTest.java into _probe_tmp/wing_selftest/,
# then runs its main(). The real class that ships in the jar is what gets tested.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/selftest_wing_animation.ps1
#   powershell -ExecutionPolicy Bypass -File tools/selftest_wing_animation.ps1 -KeepTemp
param([switch]$KeepTemp)
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
}
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $javac)) {
    throw "javac not found (JAVA_HOME=$env:JAVA_HOME); set JAVA_HOME to a JDK 21"
}

$animation = Join-Path $root "clockwork_unoffical\src\main\java\org\valkyrienskies\clockwork\content\contraptions\phys\bearing\WingAnimation.java"
$selftest = Join-Path $root "tools\WingAnimationSelfTest.java"
foreach ($file in @($animation, $selftest)) {
    if (-not (Test-Path $file)) { throw "Missing source file: $file" }
}

$out = [System.IO.Path]::GetFullPath((Join-Path $root "_probe_tmp\wing_selftest"))
$expectedPrefix = [System.IO.Path]::GetFullPath((Join-Path $root "_probe_tmp")).TrimEnd('\') + "\wing_selftest"
if (-not $out.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to touch unexpected temp directory: $out"
}
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "javac $animation"
Write-Host "      $selftest"
& $javac -encoding UTF-8 -d $out $animation $selftest
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit code $LASTEXITCODE)" }

Write-Host "java  org.valkyrienskies.clockwork.content.contraptions.phys.bearing.WingAnimationSelfTest"
& $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp $out org.valkyrienskies.clockwork.content.contraptions.phys.bearing.WingAnimationSelfTest
$exit = $LASTEXITCODE
if ($exit -ne 0) { throw "WingAnimation self-test failed (exit code $exit)" }

if (-not $KeepTemp) { Remove-Item -Recurse -Force $out }
Write-Host "Wing state-machine self-test PASSED."
exit 0
