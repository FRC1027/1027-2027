<#
.SYNOPSIS
    FRC 1027 - Automated YAGSL & YAMS Sync, Patch, and Verification Tool
.DESCRIPTION
    1. Downloads/pulls latest upstream commits from Yet-Another-Software-Suite/YAGSL and YAMS.
    2. Compares upstream source files against the local robot project.
    3. Copies updated files into src/main/java/swervelib and src/main/java/yams.
    4. Automatically checks for and reapplies Team 1027's WPILib 2027 Alpha compatibility patches.
    5. Executes './gradlew.bat compileJava test' to verify with full PASS / FAIL reporting.
    6. Automatically rolls back if compilation or unit tests fail.
#>

param(
    [string]$YagslBranch = "main",
    [string]$YamsBranch = "master",
    [string]$CacheDir = "$env:LOCALAPPDATA\FRC1027\upstream_yagsl_yams",
    [switch]$DryRun,
    [switch]$SkipTests,
    [switch]$Help
)

if ($Help) {
    Write-Host @"
Usage:
    .\tools\sync_yagsl_yams.ps1 [-YagslBranch <branch>] [-YamsBranch <branch>] [-DryRun] [-SkipTests]

Options:
    -DryRun      Inspects upstream changes and reports what would be updated without modifying files.
    -SkipTests   Skips running the Gradle compile and unit test suite.
    -CacheDir    Custom directory to store upstream Git clones (defaults to %LOCALAPPDATA%\FRC1027\upstream_yagsl_yams).
"@
    exit 0
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$swervelibTarget = "$projectRoot\src\main\java\swervelib"
$yamsTarget = "$projectRoot\src\main\java\yams"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  FRC 1027 - YAGSL & YAMS Upstream Sync & Verification Tool  " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Project Root:     $projectRoot"
Write-Host "Local Cache Dir:  $CacheDir"

# -------------------------------------------------------------
# 1. Clone / Pull Upstream Repositories
# -------------------------------------------------------------
Write-Host "`n[1/5] Fetching Latest Upstream Repositories..." -ForegroundColor Yellow
if (-not (Test-Path $CacheDir)) {
    New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null
}

$yagslRepoDir = "$CacheDir\YAGSL"
$yamsRepoDir = "$CacheDir\YAMS"

# Function to clone or pull
function Update-Repo($url, $dir, $branch) {
    if (-not (Test-Path "$dir\.git")) {
        Write-Host "  Cloning $url ($branch)..." -ForegroundColor Gray
        git clone --depth 1 -b $branch $url $dir
    } else {
        Write-Host "  Pulling latest $branch from $url..." -ForegroundColor Gray
        Push-Location $dir
        git fetch origin $branch --depth 1
        git reset --hard "origin/$branch"
        Pop-Location
    }
}

Update-Repo "https://github.com/Yet-Another-Software-Suite/YAGSL.git" $yagslRepoDir $YagslBranch
Update-Repo "https://github.com/Yet-Another-Software-Suite/YAMS.git" $yamsRepoDir $YamsBranch

$yagslSrc = "$yagslRepoDir\yagsl\java\swervelib"
$yagslExtraYams = "$yagslRepoDir\yagsl\java\yams"
$yamsSrc = "$yamsRepoDir\yams\java\yams"

if (-not (Test-Path $yagslSrc) -or -not (Test-Path $yamsSrc)) {
    Write-Host "ERROR: Upstream source paths not found ($yagslSrc or $yamsSrc)" -ForegroundColor Red
    exit 1
}

# -------------------------------------------------------------
# 2. Check What Changed
# -------------------------------------------------------------
Write-Host "`n[2/5] Comparing Upstream Source against Local Codebase..." -ForegroundColor Yellow

function Get-FileChanges($srcDir, $destDir, $prefix) {
    $changed = @()
    if (-not (Test-Path $srcDir)) { return $changed }
    $srcFiles = Get-ChildItem -Path $srcDir -Recurse -File
    foreach ($file in $srcFiles) {
        $relPath = $file.FullName.Substring($srcDir.Length).TrimStart('\', '/')
        $destFile = Join-Path $destDir $relPath
        if (-not (Test-Path $destFile)) {
            $changed += [PSCustomObject]@{ Status = "NEW"; Path = "$prefix\$relPath"; Src = $file.FullName; Dest = $destFile }
        } else {
            $srcHash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash
            $destHash = (Get-FileHash $destFile -Algorithm SHA256).Hash
            if ($srcHash -ne $destHash) {
                $changed += [PSCustomObject]@{ Status = "MODIFIED"; Path = "$prefix\$relPath"; Src = $file.FullName; Dest = $destFile }
            }
        }
    }
    return $changed
}

$yagslChanges = Get-FileChanges $yagslSrc $swervelibTarget "swervelib"
$yamsChanges = Get-FileChanges $yamsSrc $yamsTarget "yams"
$extraYamsChanges = Get-FileChanges $yagslExtraYams $yamsTarget "yams"
$allChanges = $yagslChanges + $yamsChanges + $extraYamsChanges

if ($allChanges.Count -eq 0) {
    Write-Host "  Local swervelib and yams are already identical to upstream!" -ForegroundColor Green
    if ($DryRun) { exit 0 }
} else {
    Write-Host "  Detected $($allChanges.Count) file(s) with upstream differences:" -ForegroundColor Cyan
    foreach ($c in $allChanges) {
        $color = if ($c.Status -eq "NEW") { "Green" } else { "Yellow" }
        Write-Host "    [$($c.Status)] $($c.Path)" -ForegroundColor $color
    }
}

if ($DryRun) {
    Write-Host "`n[DryRun] No files copied or modified." -ForegroundColor Magenta
    exit 0
}

# -------------------------------------------------------------
# 3. Create Backup & Copy Updated Files
# -------------------------------------------------------------
Write-Host "`n[3/5] Backing Up & Applying Upstream Files..." -ForegroundColor Yellow
$backupDir = "$projectRoot\build\sync_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Path "$backupDir\swervelib" -Force | Out-Null
New-Item -ItemType Directory -Path "$backupDir\yams" -Force | Out-Null
Copy-Item "$swervelibTarget\*" "$backupDir\swervelib\" -Recurse -Force
Copy-Item "$yamsTarget\*" "$backupDir\yams\" -Recurse -Force
Write-Host "  Created safety backup in: $backupDir" -ForegroundColor Gray

# Copy updated files
robocopy $yagslSrc $swervelibTarget /MIR /NFL /NDL /NJH /NJS | Out-Null
robocopy $yamsSrc $yamsTarget /MIR /NFL /NDL /NJH /NJS | Out-Null
if (Test-Path $yagslExtraYams) {
    robocopy $yagslExtraYams $yamsTarget /E /NFL /NDL /NJH /NJS | Out-Null
}
Write-Host "  Upstream files copied successfully." -ForegroundColor Green

# -------------------------------------------------------------
# 4. Check & Re-Apply Team 1027 WPILib 2027 Alpha Fixes
# -------------------------------------------------------------
Write-Host "`n[4/5] Checking & Reapplying WPILib 2027 Alpha Compatibility Fixes..." -ForegroundColor Yellow

$patchCount = 0

# Fix 1: Avaje JSONB Adapter Registration in SwerveParser.java
$swerveParserFile = "$swervelibTarget\parser\SwerveParser.java"
if (Test-Path $swerveParserFile) {
    $spContent = [System.IO.File]::ReadAllText($swerveParserFile)
    if (-not $spContent.Contains("SwerveDriveJsonJsonAdapter::new")) {
        Write-Host "  Re-applying Avaje JSONB explicit adapter registration in SwerveParser.java..." -ForegroundColor Yellow
        $oldPattern = "private static final Jsonb jsonb = Jsonb.builder().failOnUnknown(false).build();"
        $newPattern = @"
  private static final Jsonb jsonb = Jsonb.builder()
      .add(SwerveDriveJson.class, SwerveDriveJsonJsonAdapter::new)
      .add(PIDFPropertiesJson.class, PIDFPropertiesJsonJsonAdapter::new)
      .add(PhysicalPropertiesJson.class, PhysicalPropertiesJsonJsonAdapter::new)
      .add(ModuleJson.class, ModuleJsonJsonAdapter::new)
      .add(DeviceJson.class, DeviceJsonJsonAdapter::new)
      .add(PIDFConfig.class, PIDFConfigJsonAdapter::new)
      .add(AngleGearingJson.class, AngleGearingJsonJsonAdapter::new)
      .add(BoolMotorJson.class, BoolMotorJsonJsonAdapter::new)
      .add(DriveGearingJson.class, DriveGearingJsonJsonAdapter::new)
      .add(GearingJson.class, GearingJsonJsonAdapter::new)
      .add(LocationJson.class, LocationJsonJsonAdapter::new)
      .add(MotorConfigDouble.class, MotorConfigDoubleJsonAdapter::new)
      .add(MotorConfigInt.class, MotorConfigIntJsonAdapter::new)
      .failOnUnknown(false)
      .build();
"@
        if ($spContent.Contains($oldPattern)) {
            $spContent = $spContent.Replace($oldPattern, $newPattern)
            [System.IO.File]::WriteAllText($swerveParserFile, $spContent)
            $patchCount++
            Write-Host "    [OK] SwerveParser Avaje adapter registration restored." -ForegroundColor Green
        } else {
            Write-Host "    [WARN] Could not find default Jsonb.builder() pattern to replace in SwerveParser.java" -ForegroundColor Red
        }
    } else {
        Write-Host "  [OK] SwerveParser.java already contains Avaje adapter registration." -ForegroundColor Green
    }
}

# Fix 2: Bare Device Names in DeviceJson.java
$deviceJsonFile = "$swervelibTarget\parser\json\DeviceJson.java"
if (Test-Path $deviceJsonFile) {
    $djContent = [System.IO.File]::ReadAllText($deviceJsonFile)
    if (-not $djContent.Contains("krakenx60")) {
        Write-Host "  Re-applying bare device name mapping in DeviceJson.java..." -ForegroundColor Yellow
        # We check if type.contains("_") check needs bare name fallback
        # If upstream hasn't added bare name parsing, restore our patched version
        Copy-Item "$backupDir\swervelib\parser\json\DeviceJson.java" $deviceJsonFile -Force
        $patchCount++
        Write-Host "    [OK] DeviceJson bare name support restored from backup." -ForegroundColor Green
    } else {
        Write-Host "  [OK] DeviceJson.java already supports bare device names." -ForegroundColor Green
    }
}

# Fix 3: Alert System Collision Uniqueness in SwerveDriveTelemetry.java
$telemetryFile = "$swervelibTarget\telemetry\SwerveDriveTelemetry.java"
if (Test-Path $telemetryFile) {
    $telContent = [System.IO.File]::ReadAllText($telemetryFile)
    if (-not $telContent.Contains('"CANIdWarning"')) {
        Write-Host "  Re-applying unique Alert IDs in SwerveDriveTelemetry.java..." -ForegroundColor Yellow
        Copy-Item "$backupDir\swervelib\telemetry\SwerveDriveTelemetry.java" $telemetryFile -Force
        $patchCount++
        Write-Host "    [OK] SwerveDriveTelemetry unique Alert IDs restored." -ForegroundColor Green
    } else {
        Write-Host "  [OK] SwerveDriveTelemetry.java already has unique Alert IDs." -ForegroundColor Green
    }
}

# Fix 4: SmartMotorController Alert ID Uniqueness
$smcFile = "$yamsTarget\motorcontrollers\SmartMotorController.java"
if (Test-Path $smcFile) {
    $smcContent = [System.IO.File]::ReadAllText($smcFile)
    if (-not $smcContent.Contains('_rioClosedLoop"')) {
        Write-Host "  Re-applying unique Alert ID in SmartMotorController.java..." -ForegroundColor Yellow
        Copy-Item "$backupDir\yams\motorcontrollers\SmartMotorController.java" $smcFile -Force
        $patchCount++
        Write-Host "    [OK] SmartMotorController unique Alert ID restored." -ForegroundColor Green
    } else {
        Write-Host "  [OK] SmartMotorController.java already has unique Alert IDs." -ForegroundColor Green
    }
}

# Fix 5: CTRE Null CANBus Safety in CTREDevices.java
$ctreFile = "$swervelibTarget\parser\deserializer\reflections\CTREDevices.java"
if (Test-Path $ctreFile) {
    $ctreContent = [System.IO.File]::ReadAllText($ctreFile)
    if (-not $ctreContent.Contains("getCANBus")) {
        Write-Host "  Re-applying null CANBus safety in CTREDevices.java..." -ForegroundColor Yellow
        Copy-Item "$backupDir\swervelib\parser\deserializer\reflections\CTREDevices.java" $ctreFile -Force
        $patchCount++
        Write-Host "    [OK] CTREDevices null CANBus safety restored." -ForegroundColor Green
    } else {
        Write-Host "  [OK] CTREDevices.java already has null CANBus safety." -ForegroundColor Green
    }
}

Write-Host "  Total 2027 Alpha patches verified/reapplied: $patchCount" -ForegroundColor Cyan

# -------------------------------------------------------------
# 5. Compile & Unit Test Verification
# -------------------------------------------------------------
if (-not $SkipTests) {
    Write-Host "`n[5/5] Running Gradle Compilation & Unit Test Suite..." -ForegroundColor Yellow
    
    & "$projectRoot\gradlew.bat" compileJava test --rerun
    $testResult = $LASTEXITCODE

    if ($testResult -eq 0) {
        Write-Host @"

============================================================
  [PASS] YAGSL & YAMS SYNC AND VERIFICATION SUCCEEDED!      
============================================================
All files updated, 2027 shims verified, and unit tests passed!
"@ -ForegroundColor Green
        # Cleanup backup on clean pass
        Remove-Item -Recurse -Force $backupDir -ErrorAction SilentlyContinue
    } else {
        Write-Host @"

============================================================
  [FAIL] COMPILATION OR UNIT TESTS FAILED!                  
============================================================
Restoring previous working state from backup...
"@ -ForegroundColor Red
        
        # Rollback automatically to ensure working state
        robocopy "$backupDir\swervelib" $swervelibTarget /MIR /NFL /NDL /NJH /NJS | Out-Null
        robocopy "$backupDir\yams" $yamsTarget /MIR /NFL /NDL /NJH /NJS | Out-Null
        Write-Host "  Rollback completed. Codebase restored to pre-sync state." -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "`n[5/5] Tests skipped (-SkipTests flag passed)." -ForegroundColor Gray
}

Write-Host "`nSync operation completed successfully!" -ForegroundColor Cyan
