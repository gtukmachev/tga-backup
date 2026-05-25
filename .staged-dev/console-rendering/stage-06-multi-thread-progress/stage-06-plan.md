---
currentActivity: "All steps complete — progress output styled across all FileOps"
nextPlannedActivity: "Stage complete — proceed to stage transition"
---

# Stage 06 — Multi-thread Progress Styling

## Goals

Apply semantic colors to the multi-threaded progress output: per-worker status lines,
global status line, and error output. Replace hardcoded ANSI codes in demo.kt with
`style()` calls. After this stage, all progress output uses the styling infrastructure.

## Context from previous stages

### Already styled (Stages 04-05)
- Summary table, warnings, prompts, file/move lists (Stage 04)
- Phase headers with icons, logo title, tree loading messages (Stage 05)
- `logWrap()` ok/error suffixes (Stage 04)

### Styling infrastructure (Stages 01-02)
- `style(text, color, bold, dim)` — wraps in ANSI only when `Terminal.supportsAnsi` is true
- `Color` enum: SUCCESS, WARNING, ERROR, INFO, MUTED, ACCENT
- `Icons`: CHECK, CROSS, WARNING, INFO, ARROW, BULLET, FOLDER, FILE
- `stripAnsi()`, `visibleLength()`, `truncateToWidth()`
- `ESC = 27.toChar()` in Style.kt (avoids unicode escape encoding issues with tooling)

### ConsoleMultiThreadWorkers (Stage 03)
- Interactive mode: uses ANSI cursor movement to update lines in-place, truncates to `Terminal.width`
- Non-interactive mode: `stripAnsi()` + throttled `println()` with exponential backoff
- Already imports `stripAnsi`, `truncateToWidth` from `tga.backup.terminal`

### Important: ESC character issue
The Write tool and Bash heredocs interpret `` as the actual escape byte. To write
literal Kotlin unicode escapes, use Python scripts or the `ESC` constant from Style.kt.
Lines 135 and 155 in ConsoleMultiThreadWorkers.kt contain actual ESC bytes — they work
correctly at runtime but appear as plain `[` in the Read tool output.

## Files to modify

1. **`src/main/kotlin/tga/backup/files/LocalFileOps.kt`** — `StatusListener.printProgress()`
   - Line 181: `"$action: $shortName $percentStr% [$speedStr/s] $progressBar$errStr"`
   - Style: action in bold, percentage in ACCENT, speed in MUTED, DONE in SUCCESS, ERROR in ERROR

2. **`src/main/kotlin/tga/backup/files/YandexFileOps.kt`** — `StatusListener` (similar to Local)
   - Line ~320: same status format pattern
   - Also `scanFolder` status (line ~63): `"Fetching: $shortPath"`

3. **`src/main/kotlin/tga/backup/files/GDriveFileOps.kt`** — `StatusListener` (similar)
   - Line ~258: same status format
   - Also `scanFolder` status (line ~73): `"Fetching: $shortPath"`

4. **`src/main/kotlin/tga/backup/files/FileOps.kt`** — `SyncStatus.formatProgress()`
   - Line 249: `"Global status: ${globalPrc}%  $loadedSizeStr / $totalSizeStr ..."`
   - Style: label bold, percentage in ACCENT, sizes in MUTED, speed in INFO

5. **`src/main/kotlin/tga/backup/utils/demo.kt`** — replace hardcoded ANSI codes
   - Lines 23-28: hardcoded `[90m`, `[34m`, etc.
   - Replace with `style()` calls using semantic colors

## Step-by-step execution plan

### Step 1 — Style `StatusListener.printProgress()` in LocalFileOps.kt

The status format is: `"$action: $shortName $percentStr% [$speedStr/s] $progressBar$errStr"`

Apply styling:
- `action` → `style(action, bold = true)` (action is "Copying" or "Overriding")
- `percentStr` → `style("$percentStr%", Color.ACCENT)`
- `speedStr` → `style("[$speedStr/s]", Color.MUTED)`
- `progressBar` → leave as-is (dot-based, readable)
- `" DONE "` → `style(" DONE ", Color.SUCCESS, bold = true)` (or use `Icons.CHECK`)
- `errStr` → `style(errStr, Color.ERROR)` when non-empty

Add imports for `style`, `Color`, `Icons` from `tga.backup.terminal`.

### Step 2 — Style `StatusListener` in YandexFileOps.kt and GDriveFileOps.kt

Same pattern as Step 1. Also style the scan status messages:
- `"Fetching: $shortPath"` → `"${style("Fetching:", Color.INFO)} $shortPath"`
- `"Scanning Yandex/GDrive: N files [size]"` → style count and size

### Step 3 — Style `SyncStatus.formatProgress()` in FileOps.kt

`"Global status: ${globalPrc}%  $loadedSizeStr / $totalSizeStr $predictionStr [$speedStr/s]"`

Apply:
- `"Global status:"` → `style("Global status:", bold = true)`
- `"${globalPrc}%"` → `style("${globalPrc}%", Color.ACCENT)`
- `"$loadedSizeStr / $totalSizeStr"` → `style(..., Color.MUTED)`
- `"[$speedStr/s]"` → `style(..., Color.INFO)`

Add imports.

### Step 4 — Update demo.kt

Replace hardcoded ANSI color codes with `style()` calls:
- `"[90m"` (dark gray) → `Color.MUTED`
- `"[34m"` (blue) → `Color.INFO`
- `"[94m"` (light blue) → `Color.ACCENT`
- `"[96m"` (cyan) → `Color.ACCENT`
- Use `style(message, color)` instead of raw color codes + reset

Also style the global progress line and completion output.

### Step 5 — Verify build

Run `mvn clean test` to ensure all tests pass.

## Acceptance criteria

- [ ] Per-worker status lines have colored action, percentage, speed, and error indicators
- [ ] Global status line has bold label and colored percentage/speed
- [ ] Error lines use `Color.ERROR`
- [ ] demo.kt uses `style()` instead of hardcoded ANSI codes
- [ ] All styling degrades cleanly when `Terminal.supportsAnsi` is false
- [ ] `mvn clean test` passes
