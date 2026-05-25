---
currentActivity: "Ready to start"
nextPlannedActivity: "Create Terminal singleton with capability detection"
---

# Stage 01 — Terminal Capability Detection

## Goals

Create a `Terminal` singleton object in `tga.backup.terminal` package that detects
terminal capabilities at startup. This is the foundation for all rendering decisions
in later stages.

## Context

The codebase currently has no terminal awareness. ANSI escape codes are hardcoded
in `BackupScript.kt` (yellow warnings), `demo.kt` (colored progress), and
`ConsoleMultiThreadWorkers.kt` (cursor movement). There's no detection of terminal
width, color support, or interactive mode.

All output currently uses `println`/`print` directly with inline `[` sequences.

## Step-by-step execution plan

### Step 1 — Create `Terminal.kt`

Create `src/main/kotlin/tga/backup/terminal/Terminal.kt`:

```kotlin
package tga.backup.terminal

object Terminal {
    val isInteractive: Boolean   // System.console() != null
    val supportsAnsi: Boolean    // isInteractive && not NO_COLOR && TERM != "dumb"
    val width: Int               // from `stty size` or fallback 120
    val isDarkTheme: Boolean     // from COLORFGBG or default true
}
```

**Detection logic:**

1. **`isInteractive`**: `System.console() != null`
   - When output is piped (`> file.txt`), `System.console()` returns null.

2. **`supportsAnsi`**:
   - `false` if `!isInteractive`
   - `false` if env `NO_COLOR` is set (https://no-color.org/)
   - `true` if env `FORCE_COLOR` is set
   - `false` if env `TERM` == `"dumb"`
   - Otherwise: `true` on macOS/Linux, check Windows version for Windows

3. **`width`**:
   - Run `stty size 2>/dev/null` and parse second number
   - If that fails or is non-interactive, try env `COLUMNS`
   - Fallback: 120
   - Clamp to minimum 40

4. **`isDarkTheme`**:
   - Parse env `COLORFGBG` — format is `fg;bg`. If bg < 8, it's dark.
   - Default: `true` (dark theme assumed)

### Step 2 — Make Terminal testable

The `Terminal` object needs to be testable with different env configurations.
Use a strategy pattern internally: extract detection logic into a `TerminalDetector`
class that can be constructed with custom env/system providers for testing.

```kotlin
class TerminalDetector(
    private val getEnv: (String) -> String? = System::getenv,
    private val getConsole: () -> Any? = { System.console() },
    private val runCommand: (String) -> String? = { /* exec stty */ }
) {
    fun detect(): TerminalCapabilities
}

data class TerminalCapabilities(
    val isInteractive: Boolean,
    val supportsAnsi: Boolean,
    val width: Int,
    val isDarkTheme: Boolean,
)
```

The `Terminal` object delegates to a default `TerminalDetector`.

### Step 3 — Write tests

Create `src/test/kotlin/tga/backup/terminal/TerminalDetectorTest.kt`:

- Test `isInteractive = false` when console is null
- Test `supportsAnsi = false` when `NO_COLOR` is set
- Test `supportsAnsi = true` when `FORCE_COLOR` is set (even if non-interactive? — no, keep isInteractive requirement unless FORCE_COLOR overrides it)
- Test width parsing from stty output "24 80" → 80
- Test width fallback to COLUMNS env
- Test width fallback to 120
- Test `isDarkTheme` parsing from `COLORFGBG`
- Test `isDarkTheme` defaults to true

### Step 4 — Verify build

Run `mvn clean test` to ensure no compilation errors and all tests pass.

## Acceptance criteria

- [x] `Terminal.width` returns the actual column count on macOS
- [x] `Terminal.isInteractive` returns `false` when stdout is redirected
- [x] `Terminal.supportsAnsi` returns `false` when `NO_COLOR` is set
- [x] Unit tests cover all detection branches
- [x] `mvn clean test` passes
