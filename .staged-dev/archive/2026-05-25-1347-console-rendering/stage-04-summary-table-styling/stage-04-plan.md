---
currentActivity: "All steps complete — box-drawing table, styled warnings/prompts/logs, no hardcoded ANSI"
nextPlannedActivity: "Stage complete — proceed to stage transition"
---

# Stage 04 — Summary Table & Plan Styling

## Goals

Apply the styling infrastructure (from Stages 01-02) to the backup plan display in
`BackupScript.kt` and `logging.kt`. This is the most user-visible output — the table
users see before confirming a sync operation.

## Context from previous stages

### Available infrastructure
- `tga.backup.terminal.Terminal` — singleton with `supportsAnsi`, `isDarkTheme`, `width`
- `style(text, color, bold, dim)` — wraps in ANSI only when supported
- `Color` enum: SUCCESS, WARNING, ERROR, INFO, MUTED, ACCENT
- `Icons` object: CHECK, CROSS, WARNING, INFO, ARROW, BULLET, FOLDER, FILE
- `stripAnsi()`, `visibleLength()`, `truncateToWidth()`

### Files to modify

1. **`src/main/kotlin/tga/backup/scripts/BackupScript.kt`**:
   - `printSummary()` (lines 248-333): ASCII table with `-` and `|` separators
   - Warning messages (lines 67-75): hardcoded `[33m` yellow
   - Move action message (line 84): hardcoded yellow
   - `Continue (Y/N/m)?>` prompt (line 87)
   - `logMovesList()` (lines 234-246): hardcoded yellow for "(renamed)"
   - `printFinalSummary()` (lines 148-162): plain text results

2. **`src/main/kotlin/tga/backup/log/logging.kt`**:
   - `logFilesList()` (lines 91-102): plain numbered list
   - `logWrap()` (lines 5-17): plain "...ok" / "...error" suffixes

## Step-by-step execution plan

### Step 1 — Restyle `printSummary()` with box-drawing characters

Replace the ASCII table with Unicode box-drawing:
- Top border: `┌────┬───...─┐`
- Header separator: `├────┼───...─┤`
- Data rows: `│ ... │ ... │`
- Section separators: `├────┼───...─┤`
- Bottom border: `└────┴───...─┘`
- Bold header text using `style(text, bold = true)`
- Color the "To upload" row with `Color.ACCENT` (it's the upload total)
- Color the "To Delete" row with `Color.WARNING`
- Right-aligned numbers stay as-is (already handled by `alignRight`)
- All styling via `style()` — degrades to plain text automatically

### Step 2 — Restyle warning messages

Replace hardcoded `[33m`/`[0m` with `style()` and `Icons`:
- `WARNING: The 'no-overriding'...` → `style("${Icons.WARNING} WARNING: ...", Color.WARNING)`
- `WARNING: The 'no-deletion'...` → same pattern
- `Moving/Renaming actions detected...` → `style("${Icons.INFO} ...", Color.WARNING)`
- Remove the local `val yellow`/`val reset` variables

### Step 3 — Style the prompt

Change `Continue (Y/N/m)?>` to use bold styling:
```kotlin
print(style("Continue (Y/N/m)?> ", bold = true))
```

### Step 4 — Style `logMovesList()`

Replace hardcoded yellow in `logMovesList()` with `style()`:
- `(renamed)` marker: `style("(renamed)", Color.WARNING)`
- Arrow: use `Icons.ARROW` instead of `--->`
- Remove local `val yellow`/`val reset`

### Step 5 — Style `logFilesList()` in logging.kt

- Add file icon: `Icons.FILE` before each file name
- Color the size brackets with `Color.MUTED`
- Number color: `Color.MUTED`

### Step 6 — Style `logWrap()` suffixes

- `...ok` → `style("...ok", Color.SUCCESS)`
- `...Error: ...` → `style("...${error}", Color.ERROR)`

### Step 7 — Style `printFinalSummary()`

- Success count: `style(count, Color.SUCCESS)`
- Error count: `style(count, Color.ERROR)` (when > 0)
- Error details: `style(message, Color.ERROR)`

### Step 8 — Verify build

Run `mvn clean test` — no new tests needed for visual styling, but all existing tests
must still pass.

## Acceptance criteria

- [ ] Summary table uses box-drawing characters (┌─┬─┐ etc.)
- [ ] Warning messages use `style()` instead of hardcoded ANSI codes
- [ ] `logMovesList` uses `Icons.ARROW` and `style()` for renamed marker
- [ ] `logWrap` uses colored ok/error suffixes
- [ ] `printFinalSummary` has colored success/error counts
- [ ] All styling degrades cleanly when `Terminal.supportsAnsi` is false
- [ ] No hardcoded `` escape sequences remain in BackupScript.kt
- [ ] `mvn clean test` passes
