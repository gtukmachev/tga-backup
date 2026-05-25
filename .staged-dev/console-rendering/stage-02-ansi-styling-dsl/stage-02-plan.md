---
currentActivity: "Ready to start"
nextPlannedActivity: "Create Color enum and style() function in Style.kt"
---

# Stage 02 — ANSI Styling DSL

## Goals

Create a minimal styling API in `tga.backup.terminal` that:
1. Wraps text in ANSI color/attribute codes — but only when `Terminal.supportsAnsi` is true
2. Provides semantic color names (success, warning, error, info, muted, accent) with dark/light variants
3. Truncates text to a given width, correctly handling embedded ANSI escape sequences
4. Defines Unicode icon constants that degrade to ASCII when ANSI is off

## Context from Stage 01

- `Terminal` object lives in `tga.backup.terminal` package
- `Terminal.supportsAnsi` — whether to emit ANSI codes
- `Terminal.isDarkTheme` — which color variant to use
- `TerminalCapabilities` data class holds all detected values
- `TerminalDetector` is injectable for testing

Existing ANSI usage in the codebase (to be replaced in later stages):
- `BackupScript.kt:67-68,237-238` — hardcoded yellow/reset for warnings
- `demo.kt:23-29` — hardcoded color codes for progress display
- `ConsoleMultiThreadWorkers.kt:116,122` — cursor movement codes (separate concern, Stage 03)

## Step-by-step execution plan

### Step 1 — Create `Style.kt` with Color enum and `style()` function

Create `src/main/kotlin/tga/backup/terminal/Style.kt`:

```kotlin
package tga.backup.terminal

enum class Color(val darkCode: Int, val lightCode: Int) {
    SUCCESS(32, 32),     // green
    WARNING(33, 33),     // yellow
    ERROR(31, 91),       // red / bright red
    INFO(36, 34),        // cyan / blue
    MUTED(90, 37),       // dark gray / light gray
    ACCENT(96, 34),      // bright cyan / blue
}
```

The `style()` function:
```kotlin
fun style(
    text: String,
    color: Color? = null,
    bold: Boolean = false,
    dim: Boolean = false,
    supportsAnsi: Boolean = Terminal.supportsAnsi,
    isDarkTheme: Boolean = Terminal.isDarkTheme,
): String
```

- If `!supportsAnsi`, return `text` unchanged
- Build ANSI prefix from: bold (code 1), dim (code 2), color (from enum based on theme)
- Wrap: `"[${codes}m${text}[0m"`
- If no attributes requested, return text unchanged

### Step 2 — Add `stripAnsi()` and `visibleLength()` utilities

These are needed for truncation and width measurement:

```kotlin
private val ANSI_REGEX = Regex("\\[[0-9;]*m")

fun stripAnsi(text: String): String = text.replace(ANSI_REGEX, "")

fun visibleLength(text: String): Int = stripAnsi(text).length
```

### Step 3 — Add `truncateToWidth()` function

```kotlin
fun truncateToWidth(text: String, maxWidth: Int): String
```

Logic:
- If `visibleLength(text) <= maxWidth`, return as-is
- Walk through the string character by character, tracking visible character count
- Skip ANSI sequences (don't count them toward width)
- When visible count reaches `maxWidth - 1`, append "…" and close with reset if any ANSI was open
- Return the truncated string

### Step 4 — Add `Icons` object

```kotlin
object Icons {
    // Each icon has a unicode form and an ASCII fallback
    val CHECK: String    // ✔ or [OK]
    val CROSS: String    // ✖ or [FAIL]
    val WARNING: String  // ⚠ or [!]
    val INFO: String     // ℹ or [i]
    val ARROW: String    // → or ->
    val BULLET: String   // • or *
    val FOLDER: String   // 📁 or [D]
    val FILE: String     // 📄 or [F]
}
```

Selection based on `Terminal.supportsAnsi` (queried once at init, or lazily).

### Step 5 — Write tests

Create `src/test/kotlin/tga/backup/terminal/StyleTest.kt`:

- `style()` returns plain text when `supportsAnsi = false`
- `style()` wraps text with ANSI codes when `supportsAnsi = true`
- `style()` applies bold/dim attributes
- `style()` uses dark theme colors by default
- `style()` uses light theme colors when `isDarkTheme = false`
- `stripAnsi()` removes ANSI codes
- `visibleLength()` returns correct length for styled text
- `truncateToWidth()` returns text unchanged when within limit
- `truncateToWidth()` truncates with ellipsis when exceeding limit
- `truncateToWidth()` handles text with embedded ANSI codes
- `truncateToWidth()` handles text shorter than maxWidth
- Icons return unicode when ANSI is supported, ASCII when not

### Step 6 — Verify build

Run `mvn clean test` to ensure no compilation errors and all tests pass.

## Acceptance criteria

- [ ] `style("hello", Color.SUCCESS)` returns ANSI-wrapped string when supported, plain "hello" otherwise
- [ ] `style()` with `bold = true` includes bold ANSI code
- [ ] `truncateToWidth()` correctly handles strings with embedded ANSI codes
- [ ] `visibleLength()` ignores ANSI escape sequences
- [ ] Icons degrade to ASCII equivalents when `supportsAnsi` is false
- [ ] Unit tests cover all style/truncation/icon branches
- [ ] `mvn clean test` passes
