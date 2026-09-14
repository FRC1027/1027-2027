<#
.SYNOPSIS
    FRC 1027 - WPILib 2027 Alpha Compatibility & Migration Diagnostic Script
.DESCRIPTION
    Automates validation, native PE inspection, shim compilation, and simulation testing
    for WPILib 2027 Alphas (Alpha 7, Alpha 8, etc.).
#>

param(
    [switch]$RecompileShim,
    [switch]$RunSimTest,
    [switch]$Help
)

if ($Help) {
    Write-Host @"
Usage:
    .\tools\alpha_upgrade_helper.ps1 [-RecompileShim] [-RunSimTest]

Options:
    -RecompileShim   Recompiles tools/native/revshim.dll using MSVC (if VS BuildTools is installed).
    -RunSimTest      Launches a 5-second headless simulation smoke test to verify stability.
"@
    exit 0
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  FRC 1027 - WPILib 2027 Alpha Upgrade & Compatibility Tool " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Project Directory: $projectRoot"

# 1. Check WPILib & Java Environment
Write-Host "`n[1/5] Checking Java & Gradle Environment..." -ForegroundColor Yellow
$javaExe = "C:\Users\Public\wpilib\2027_alpha7\jdk\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaExe = $cmd.Source }
}
if ($javaExe) {
    $javaVer = & $javaExe -version 2>&1 | Select-Object -First 1
    Write-Host "  Found Java: $javaExe ($javaVer)" -ForegroundColor Green
} else {
    Write-Host "  WARNING: Java 25 compiler not found in standard WPILib path." -ForegroundColor Red
}

$gradleRIO = Select-String -Path "$projectRoot\build.gradle" -Pattern 'id "org.wpilib.GradleRIO" version "(.*)"'
if ($gradleRIO) {
    Write-Host "  GradleRIO Version: $($gradleRIO.Matches.Groups[1].Value)" -ForegroundColor Green
}

# 2. Check & Run Bytecode Compatibility Shims (Pathplanner / WPILib API bridge)
Write-Host "`n[2/5] Validating Java Bytecode Shims (tools/GenerateWpiCompatShims.java)..." -ForegroundColor Yellow
$shimSrc = "$projectRoot\tools\GenerateWpiCompatShims.java"
if (Test-Path $shimSrc) {
    Write-Host "  Found GenerateWpiCompatShims.java." -ForegroundColor Green
    Write-Host "  Running Gradle compilation to update bytecode shims..."
    & "$projectRoot\gradlew.bat" compileJava generateWpiCompatShims -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Java compatibility shims generated successfully!" -ForegroundColor Green
    } else {
        Write-Host "  ERROR: generateWpiCompatShims task failed." -ForegroundColor Red
    }
} else {
    Write-Host "  WARNING: tools/GenerateWpiCompatShims.java not found." -ForegroundColor Red
}

# 3. Native DLL Extraction & PE Import Inspection
Write-Host "`n[3/5] Extracting & Inspecting Native Binaries..." -ForegroundColor Yellow
& "$projectRoot\gradlew.bat" extractReleaseNative -q
$releaseDir = "$projectRoot\build\jni\release"
$revLibWpi = "$releaseDir\REVLibWpi.dll"
$wpiUtil = "$releaseDir\wpiutil.dll"
$revShim = "$releaseDir\revshim.dll"

if ((Test-Path $revLibWpi) -and (Test-Path $wpiUtil)) {
    $wpiBytes = [System.IO.File]::ReadAllBytes($wpiUtil)
    $wpiText = [System.Text.Encoding]::ASCII.GetString($wpiBytes)
    
    $revBytes = [System.IO.File]::ReadAllBytes($revLibWpi)
    $revText = [System.Text.Encoding]::ASCII.GetString($revBytes)
    
    Write-Host "  Checking REVLibWpi.dll PE imports:"
    $isPatched = $revText.Contains("revshim.dll")
    $revNeedsLegacyNow = $revText.Contains("?Now@util@wpi@@YA_KXZ")
    $revNeedsModernNow = $revText.Contains("?Now@util@wpi@@YA_JXZ")

    if ($revNeedsModernNow) {
        Write-Host "    [OK] REVLibWpi.dll is modern (alpha-7+) and natively compatible with WPILib 2027." -ForegroundColor Green
    } elseif ($isPatched) {
        Write-Host "    [OK] Legacy REVLibWpi.dll is patched to import 'revshim.dll'." -ForegroundColor Green
    } else {
        Write-Host "    [NOTICE] Legacy REVLibWpi.dll currently imports 'wpiutil.dll' (requires patching if legacy)." -ForegroundColor Yellow
    }

    Write-Host "  Checking wpiutil.dll export signatures:"
    $hasOldNow = $wpiText.Contains("?Now@util@wpi@@YA_KXZ")
    $hasNewNow = $wpiText.Contains("?Now@util@wpi@@YA_JXZ")
    $hasOldWait = $wpiText.Contains("?WaitForObject@util@wpi@@YA_NI@Z")
    $hasNewWait = $wpiText.Contains("?WaitForObject@util@wpi@@YA_NH@Z")
    $hasVFormat = $wpiText.Contains("?vformat@v12@fmt")

    Write-Host "    wpi::util::Now (uint64_t _K): " -NoNewline; if ($hasOldNow) { Write-Host "PRESENT" -ForegroundColor Green } else { Write-Host "ABSENT" -ForegroundColor Red }
    Write-Host "    wpi::util::Now (int64_t _J):  " -NoNewline; if ($hasNewNow) { Write-Host "PRESENT" -ForegroundColor Green } else { Write-Host "ABSENT" -ForegroundColor Red }
    Write-Host "    fmtlib v12 ?vformat:         " -NoNewline; if ($hasVFormat) { Write-Host "PRESENT" -ForegroundColor Green } else { Write-Host "ABSENT" -ForegroundColor Red }

    if ($revNeedsModernNow) {
        Write-Host "    --> REVLib natively matches WPILib exports. No shim required!" -ForegroundColor Green
    } elseif (-not $hasOldNow -or -not $hasOldWait -or -not $hasVFormat) {
        if (-not $isPatched) {
            Write-Host "    --> wpiutil.dll is missing symbols required by legacy REVLib. Shim is REQUIRED." -ForegroundColor Magenta
        } else {
            Write-Host "    --> Shim is active and bridging missing symbols successfully." -ForegroundColor Green
        }
    } else {
        Write-Host "    --> WPILib exports all legacy symbols. revshim.dll may not be required for this release!" -ForegroundColor Cyan
    }
} else {
    Write-Host "  Native binaries not yet extracted. Run 'gradlew extractReleaseNative' first." -ForegroundColor Yellow
}

# 4. Optional Recompilation of revshim.dll
if ($RecompileShim) {
    Write-Host "`n[4/5] Recompiling revshim.dll using MSVC..." -ForegroundColor Yellow
    $candidateVcvars = @(
        "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat"
    )
    $vcvars = $candidateVcvars | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($vcvars) {
        $cmd = "call `"$vcvars`" && cd /d `"$projectRoot\tools\native`" && cl.exe /LD /O2 /MD revshim.cpp /link /DEF:revshim.def /OUT:revshim.dll"
        cmd.exe /c $cmd
        if ($LASTEXITCODE -eq 0) {
            Remove-Item "$projectRoot\tools\native\revshim.exp", "$projectRoot\tools\native\revshim.lib", "$projectRoot\tools\native\revshim.obj" -Force -ErrorAction SilentlyContinue
            Write-Host "  revshim.dll recompiled successfully!" -ForegroundColor Green
            Copy-Item "$projectRoot\tools\native\revshim.dll" "$releaseDir\revshim.dll" -Force -ErrorAction SilentlyContinue
        } else {
            Write-Host "  ERROR: MSVC compilation failed." -ForegroundColor Red
        }
    } else {
        Write-Host "  MSVC vcvars64.bat not found. Please install Visual Studio C++ Build Tools to recompile." -ForegroundColor Red
    }
} else {
    Write-Host "`n[4/5] Precompiled revshim.dll status: " -NoNewline
    if (Test-Path "$projectRoot\tools\native\revshim.dll") {
        Write-Host "READY in tools/native/revshim.dll" -ForegroundColor Green
    } else {
        Write-Host "MISSING in tools/native/revshim.dll" -ForegroundColor Red
    }
}

# 5. Optional Simulation Smoke Test
if ($RunSimTest) {
    Write-Host "`n[5/5] Running 5-Second Simulation Smoke Test..." -ForegroundColor Yellow
    & "$projectRoot\gradlew.bat" simulateExternalJava -q
    
    $cp = (Get-Content "$projectRoot\build\runtime_classpath.txt" -ErrorAction SilentlyContinue)
    if (-not $cp) {
        # Fallback to generating runtime classpath if txt file is not yet cached
        $cp = "$projectRoot\build\classes\java\main;$projectRoot\build\resources\main"
    }
    
    $argfile = "$env:TEMP\sim_smoke_test.argfile"
    [System.IO.File]::WriteAllText($argfile, "-cp`n$cp`n")
    
    $env:HALSIM_EXTENSIONS = "$releaseDir\halsim_gui.dll;$releaseDir\halsim_ds_socket.dll;"
    $env:PATH = "$releaseDir;C:\Windows\system32;"
    
    $logOut = "$projectRoot\sim_smoke_test.log"
    $logErr = "$projectRoot\sim_smoke_test_err.log"
    Remove-Item $logOut, $logErr -Force -ErrorAction SilentlyContinue

    $proc = Start-Process -FilePath $javaExe -ArgumentList "@$argfile", "first.Main" -PassThru -NoNewWindow -RedirectStandardOutput $logOut -RedirectStandardError $logErr
    Start-Sleep -Seconds 8
    if (!$proc.HasExited) {
        Write-Host "  SUCCESS: Robot simulation started and stayed running stably without crashing!" -ForegroundColor Green
        Stop-Process -Id $proc.Id -Force
    } else {
        Write-Host "  CRASH DETECTED: Simulation exited with code: $($proc.ExitCode)" -ForegroundColor Red
        Write-Host "  --- Last 10 lines of Error Log ---"
        Get-Content $logErr -Tail 10
    }
    Remove-Item $argfile -Force -ErrorAction SilentlyContinue
    Remove-Item $logOut -Force -ErrorAction SilentlyContinue
    Remove-Item $logErr -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "`n[5/5] Simulation test skipped (pass -RunSimTest to test)." -ForegroundColor Gray
}

Write-Host "`nAll compatibility checks completed!" -ForegroundColor Cyan
