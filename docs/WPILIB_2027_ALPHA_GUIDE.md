# FRC Team 1027 - WPILib 2027 Alpha Architecture & Troubleshooting Guide

Welcome to the WPILib 2027 Alpha modernization handbook! This document is written for students and mentors to explain both **how** the 2027 Alpha migration was solved and **why** the underlying computer science systems behave the way they do.

It also contains a complete **Playbook for WPILib 2027 Alpha 8** so you can diagnose and resolve future alpha releases.

---

## Table of Contents
1. [The "Vendor Lag" Phenomenon](#1-the-vendor-lag-phenomenon)
2. [Deep-Dive: The 4 Major Incompatibilities & How We Fixed Them](#2-deep-dive-the-4-major-incompatibilities--how-we-fixed-them)
   - [A. JSON Serialization: From Jackson to Avaje JSONB](#a-json-serialization-from-jackson-to-avaje-jsonb)
   - [B. Java Bytecode Descriptors & The Class-File API](#b-java-bytecode-descriptors--the-class-file-api)
   - [C. C++ Name Mangling & Windows PE Dynamic Linking](#c-c-name-mangling--windows-pe-dynamic-linking)
   - [D. Alert System ID Uniqueness](#d-alert-system-id-uniqueness)
3. [The Alpha Upgrade Helper Tool (`alpha_upgrade_helper.ps1`)](#3-the-alpha-upgrade-helper-tool)
4. [Step-by-Step Playbook for WPILib 2027 Alpha 8](#4-step-by-step-playbook-for-wpilib-2027-alpha-8)

---

## 1. The "Vendor Lag" Phenomenon

During the FIRST Robotics offseason, WPILib developers test fundamental architectural updates:
- Upgrading to **Java 25 (LTS)** and the **SystemCore** platform.
- Cleaning up legacy technical debt (removing Hungarian `k` variable prefixes like `kZero`, adopting C++23 standards, eliminating slow reflection libraries).

However, 3rd-party vendors (Pathplanner, REV Robotics, CTRE, Studica, ThriftyBot) compile their libraries against specific alpha snapshots. When WPILib releases a new alpha (e.g. Alpha 7), vendor binaries may still be on Alpha 3 or Alpha 5.

When an unaligned vendor library runs on a newer WPILib runtime, the code will fail at runtime unless we bridge the gap. Rather than being blocked waiting weeks for vendors to publish patches, software engineers use **bytecode shims** and **native dynamic link library (DLL) shims** to maintain compatibility.

---

## 2. Deep-Dive: The 4 Major Incompatibilities & How We Fixed Them

### A. JSON Serialization: From Jackson to Avaje JSONB

#### Why WPILib Switched
Historically, WPILib and robot teams used `com.fasterxml.jackson` for JSON parsing. Jackson relies heavily on **runtime reflection**—it inspects class fields, constructors, and annotations while the robot program is running. On embedded processors (like the roboRIO and SystemCore), reflection creates high startup latency and large memory garbage collection overhead.

WPILib 2027 adopted **Avaje JSONB**, a compile-time code generator. Avaje generates serialization code during `javac` compilation, making it reflection-free and instantaneous.

#### The Bug: `No JsonAdapter for class SwerveDriveJson`
When simulation launched, Avaje threw an `IllegalArgumentException` stating that no adapter existed for our swerve classes.

#### Root Cause: Java's Unnamed Package & `ServiceLoader`
1. Avaje's annotation processor inspects all classes marked with `@Json`. It attempts to find the "common parent package" (`TopPackage.of(...)`) across all annotated classes to place the generated `GeneratedJsonComponent`.
2. Our codebase had `@Json` classes in **two distinct root packages**: `swervelib.*` and `frc.robot.*`.
3. Because there was no common parent package (e.g. `swervelib` and `frc` share nothing above them), the parent package evaluated to `null`.
4. Avaje defaulted to generating `GeneratedJsonComponent` in Java's **unnamed (default) package**.
5. Under Java's module and `ServiceLoader` specifications, provider classes located in the unnamed package **cannot be discovered** by `ServiceLoader.load()`. Therefore, zero adapters were registered at runtime!

#### The Fix: Explicit Factory Registration
Instead of relying on automated `ServiceLoader` discovery, we registered the generated adapters explicitly in [SwerveParser.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/swervelib/parser/SwerveParser.java) and [Elastic.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/frc/robot/util/Elastic.java):
```java
private static final Jsonb jsonb = Jsonb.builder()
    .add(SwerveDriveJson.class, SwerveDriveJsonJsonAdapter::new)
    .add(ModuleJson.class, ModuleJsonJsonAdapter::new)
    .add(PhysicalPropertiesJson.class, PhysicalPropertiesJsonJsonAdapter::new)
    .failOnUnknown(false)
    .build();
```
*Takeaway:* Explicit registration is deterministic, faster, and immune to package hierarchy quirks.

---

### B. Java Bytecode Descriptors & The Class-File API

#### Why Pathplanner Crashed with `NoSuchMethodError`
When starting autonomous routines, Pathplanner threw:
```
java.lang.NoSuchMethodError: 'edu.wpi.first.math.geometry.Rotation2d edu.wpi.first.math.geometry.Translation2d.getAngle()'
```
In WPILib 2027 Alpha 7, the method signature was changed:
- **WPILib 2026 / Alpha 3:** `Rotation2d getAngle()`
- **WPILib 2027 Alpha 7:** `Optional<Rotation2d> getAngle()`

In Java source code, changing a return type might look like a simple method change. But in **Java Virtual Machine (JVM) bytecode**, every method call is identified by its **Method Descriptor**:
- Pathplanner compiled against: `()Ledu/wpi/first/math/geometry/Rotation2d;`
- WPILib Alpha 7 provided: `()Ljava/util/Optional;`

At the bytecode level, these are completely different methods. When the JVM executed Pathplanner's bytecode, it could not find a method matching that exact descriptor, triggering a fatal `NoSuchMethodError`.

Additionally, WPILib Alpha 7 renamed constants from Hungarian notation (`kZero`, `kPi`) to standard Java screaming-snake-case (`ZERO`, `PI`), causing `NoSuchFieldError`.

#### The Fix: Java 25 Class-File API Shim Generator
Rather than maintaining a fork of Pathplanner or waiting for an upstream release, we built [tools/GenerateWpiCompatShims.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/tools/GenerateWpiCompatShims.java).

Using Java 25's new built-in Class-File API (`java.lang.classfile.*`), the script reads `Translation2d.class`, `Rotation2d.class`, and `Pose2d.class` from the WPILib JAR, injects synthetic bridge methods and field aliases, and outputs them to our project's classpath:
1. Injects `Rotation2d getAngle()` as a synthetic bridge calling `getAngle().orElseGet(Rotation2d::new)`.
2. Injects static field aliases:
   - `Translation2d.kZero` -> mirrors `Translation2d.ZERO`
   - `Rotation2d.kZero`, `kPi`, `kCW_90deg` -> mirrors `ZERO`, `PI`, `CW_90DEG`
   - `Pose2d.kZero` -> mirrors `Pose2d.ZERO`
3. Adds compile-time source bridges [Pair.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/org/wpilib/math/util/Pair.java) and [Alert.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/org/wpilib/driverstation/Alert.java) to redirect old package locations to `org.wpilib.util.*`.

In `build.gradle`, the task `generateWpiCompatShims` automatically runs right after `compileJava`.

---

### C. C++ Name Mangling & Windows PE Dynamic Linking

#### Why REVLib Crashed Simulation with `EXCEPTION_ACCESS_VIOLATION`
When initializing a REV `SparkMax` motor in simulation, the terminal printed:
```
Unable to find wpi driver binary
EXCEPTION_ACCESS_VIOLATION (0xc0000005) in c_SIM_Spark_Create+0xbf
```

#### Understanding C++ Name Mangling
In C++, functions with the same name can have different parameter types (function overloading). To support this in compiled binaries, C++ compilers "mangle" function names into unique symbol strings containing type codes.

In MSVC x64:
- `_K` represents `unsigned __int64` (`uint64_t`)
- `_J` represents `signed __int64` (`int64_t`)
- `I` represents `unsigned int` (`uint32_t`)
- `H` represents `signed int` (`int32_t`)

#### What Changed in Alpha 7?
REVLib Alpha 6's native DLL `REVLibWpi.dll` imported 3 symbols from WPILib's `wpiutil.dll`:
1. `?Now@util@wpi@@YA_KXZ` (`uint64_t Now()`)
2. `?WaitForObject@util@wpi@@YA_NI@Z` (`bool WaitForObject(uint32_t)`)
3. `?vformat@v12@fmt...` (fmtlib v12 string formatting)

In WPILib Alpha 7:
1. `wpi::util::Now()` changed its return type to `int64_t`, exporting `?Now@util@wpi@@YA_JXZ` instead of `_KXZ`.
2. `wpi::util::WaitForObject()` changed its parameter type to `int32_t`, exporting `?WaitForObject@util@wpi@@YA_NH@Z` instead of `_NI@Z`.
3. WPILib upgraded to C++23 `std::format` and deleted fmtlib v12 from `wpiutil.dll`.

#### Why the Simulation Crashed
When `BackendDriver.dll` attempted to load `REVLibWpi.dll`, the Windows Portable Executable (PE) loader inspected its Import Address Table (IAT). Because `wpiutil.dll` no longer exported those exact mangled symbols, the OS rejected the load with **Win32 Error 127: `ERROR_PROC_NOT_FOUND`**.

`BackendDriver.dll` reported `Unable to find wpi driver binary` and continued. When `c_SIM_Spark_Create` later tried to call through the uninitialized driver pointer, it dereferenced address `0x00000000` (`NULL`), producing an immediate Access Violation (segmentation fault).

#### The Solution: `revshim.dll` and Surgical PE String Patching
1. **The Shim DLL:**
   We wrote [tools/native/revshim.cpp](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/tools/native/revshim.cpp) and [revshim.def](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/tools/native/revshim.def). This lightweight DLL exports all three legacy symbols expected by REVLib and forwards calls to the Alpha 7 WPILib functions at runtime.
2. **The Surgical PE Binary Patch:**
   In `REVLibWpi.dll`, the string table specifies which DLL to import.
   - `"wpiutil.dll"` is exactly 11 ASCII characters.
   - `"revshim.dll"` is exactly 11 ASCII characters.
   Because both strings are identical in length, we can replace `"wpiutil.dll"` with `"revshim.dll"` directly in the binary without modifying file offsets, section boundaries, or PE header checksums!
3. **Gradle Automation:**
   In [build.gradle](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/build.gradle), the `patchRevLibNative` task runs immediately after `extractReleaseNative` and `extractDebugNative`. It automatically copies `revshim.dll` into `build/jni/` and patches `REVLibWpi.dll`.

---

### D. Alert System ID Uniqueness

In WPILib 2026, creating multiple `Alert` instances with the same name was silently permitted. In WPILib 2027, the Alert manager maintains a registry and throws `org.wpilib.util.AlertException: Alert already allocated` if two alerts share the same ID.

We resolved this in [SwerveDriveTelemetry.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/swervelib/telemetry/SwerveDriveTelemetry.java) and [SmartMotorController.java](file:///c:/AI_Projects/FRC2027_alpha7/1027-2027_alpha7/src/main/java/yams/motorcontrollers/SmartMotorController.java) by using the 4-argument constructor with unique IDs:
```java
new Alert("SwerveDrive", "SwerveDrive_" + uniqueModuleId, "Motor fault detected", AlertType.kWarning);
```

---

## 3. The Alpha Upgrade Helper Tool

We have provided a diagnostic tool in the repository:
```powershell
.\tools\alpha_upgrade_helper.ps1
```

### What It Does
1. **Environment Check:** Verifies Java 25 compiler and GradleRIO version.
2. **Bytecode Shim Validation:** Runs `generateWpiCompatShims` and verifies class file injection.
3. **Native PE Inspection:** Extracts native DLLs and checks `wpiutil.dll` export tables against `REVLibWpi.dll` import tables to detect missing symbols before running simulation.
4. **Shim Recompilation (Optional):** `-RecompileShim` recompiles `revshim.dll` using MSVC C++ Build Tools if changes are made to `revshim.cpp`.
5. **Simulation Smoke Test (Optional):** `-RunSimTest` launches robot simulation headlessly for 5 seconds and monitors standard output/error to ensure no native crashes occur.

---

## 4. The YAGSL & YAMS Upstream Sync Tool (`sync_yagsl_yams.ps1`)

Because YAGSL and YAMS source trees are currently inlined into `src/main/java/`, we provide an automated synchronization tool:
```powershell
powershell -ExecutionPolicy Bypass -File .\tools\sync_yagsl_yams.ps1 [-DryRun] [-SkipTests]
```

### What It Does:
1. **Pulls Upstream Repositories:** Clones or updates local clones of [Yet-Another-Software-Suite/YAGSL](https://github.com/Yet-Another-Software-Suite/YAGSL) and [Yet-Another-Software-Suite/YAMS](https://github.com/Yet-Another-Software-Suite/YAMS).
2. **Compares & Copies:** Detects all modified/new files and copies them into `src/main/java/swervelib` and `src/main/java/yams`.
3. **Safety Backup:** Automatically creates a rollback snapshot in `build/sync_backup_<timestamp>` before making changes.
4. **Reapplies 2027 Alpha Patches:** Automatically checks for and preserves our 5 critical 2027 Alpha fixes:
   - Avaje JSONB explicit adapter registration in `SwerveParser.java`.
   - Bare device name parsing (`krakenx60`, `pigeon2`, etc.) in `DeviceJson.java`.
   - Unique Alert IDs in `SwerveDriveTelemetry.java` and `SmartMotorController.java`.
   - Null-safe CANBus fallback in `CTREDevices.java`.
5. **Compiles & Runs Tests:** Executes `./gradlew.bat compileJava test --rerun`:
   - **PASS:** Outputs green confirmation banner and cleans up temporary backup.
   - **FAIL:** Outputs red failure banner and **automatically rolls back** to the pre-sync working state so the robot code is never left broken.

---

## 5. Step-by-Step Playbook for WPILib 2027 Alpha 8

When WPILib 2027 Alpha 8 (or any future alpha/beta) is released, follow this checklist:

### Step 1: Update Build Files
1. Open `build.gradle` and update the plugin version:
   ```groovy
   id "org.wpilib.GradleRIO" version "2027.0.0-alpha-8"
   ```
2. Open `.wpilib/wpilib_preferences.json` and update:
   ```json
   "wpilibVersion": "2027.0.0-alpha-8"
   ```

### Step 2: Check for Updated Vendordeps
Check if vendors have published Alpha 8 vendordeps:
- In VS Code: Press `Ctrl+Shift+P` -> `WPILib: Manage Vendor Libraries` -> `Check for updates (online)`.
- If REV Robotics or Pathplanner released updates, install them.

### Step 3: Run the Upgrade Helper
Run the helper tool from PowerShell in the project root:
```powershell
powershell -ExecutionPolicy Bypass -File .\tools\alpha_upgrade_helper.ps1 -RunSimTest
```

### Step 4: Interpret the Diagnostic Results
- **Scenario A: All checks pass and simulation starts cleanly.**
  You're done! Everything is fully compatible.
- **Scenario B: wpiutil.dll exports all legacy symbols (or REV released an Alpha 8 build).**
  The script will report:
  `WPILib exports all legacy symbols. revshim.dll may not be required for this release!`
  In that case, you can remove `patchRevLibNative` from `build.gradle`.
- **Scenario C: New missing symbols are reported in `wpiutil.dll`.**
  If WPILib altered additional function signatures:
  1. Open `tools/native/revshim.cpp` and add the stub or forwarder for the new function.
  2. Add the mangled symbol name to `tools/native/revshim.def`.
  3. Recompile the shim:
     ```powershell
     .\tools\alpha_upgrade_helper.ps1 -RecompileShim -RunSimTest
     ```
- **Scenario D: Java bytecode `NoSuchMethodError` on another library.**
  If Pathplanner or another vendor complains of a missing method:
  1. Note the class and method name from the stack trace.
  2. Open `tools/GenerateWpiCompatShims.java`.
  3. Add the bridge method or field alias using the `ClassFile` API.
  4. Run `.\gradlew.bat compileJava generateWpiCompatShims`.

### Step 5: Verify with Tests & VS Code Simulation
```powershell
.\gradlew.bat test
```
Then launch simulation in VS Code via `F5` or `WPILib: Simulate Robot Code`. Confirm all drive telemetry and mechanisms respond as expected!
