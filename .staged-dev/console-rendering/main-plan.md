---
currentStage: 06-multi-thread-progress
currentStagePath: .staged-dev/console-rendering/stage-06-multi-thread-progress
branch: feature/console-rendering
codeReviewTool: roborev-review-branch
---

# Console Rendering — Pretty, Robust Terminal Output

## Goal

Make the TGA Backup utility's console output look professional and human-friendly:
colored, with icons, aligned columns, tables with alternating row styles, bold headers —
while gracefully degrading when the terminal doesn't support ANSI escapes (e.g., output
redirected to a file).

## Stages

┌────┬───────────────────────────────────────┬─────────────┐
│ #  │ Stage                                 │ Status      │
├────┼───────────────────────────────────────┼─────────────┤
│ 00 │ Planning                              │ 🟢 Done     │
│ 01 │ Terminal capability detection         │ 🟢 Done     │
│ 02 │ ANSI styling DSL                      │ 🟢 Done     │
│ 03 │ Throttled non-interactive output       │ 🟢 Done     │
│ 04 │ Summary table & plan styling           │ 🟢 Done     │
│ 05 │ Phase headers, logos & logging         │ 🟢 Done     │
│ 06 │ Multi-thread progress styling          │ 🔵 Active   │
│ 07 │ End-to-end integration & polish        │ ⬜ Pending  │
│ 08 │ Final verification & cleanup           │ ⬜ Pending  │
└────┴───────────────────────────────────────┴─────────────┘

## Problem Analysis

The codebase currently has:
- **Raw `println`/`print` output** everywhere (Main.kt, BackupScript.kt, logging.kt, treeLoader.kt, FileOps.kt, logo.kt)
- **Hardcoded ANSI codes** scattered in BackupScript (yellow warnings) and demo.kt (colored progress)
- **ConsoleMultiThreadWorkers** uses ANSI cursor movement with no terminal width awareness — long lines break the multi-line layout
- **No detection** of terminal capabilities (color support, width, dark/light theme)
- **No throttling** of output in non-interactive mode (pipe to file)

## Architecture

The work is organized into **3 Phases**, each containing multiple stages.
Phases are independent problem domains; stages within a phase are sequential.

---

## Phase 1: Terminal Capability Detection & Infrastructure

Foundation layer — detect what the terminal can do, and provide a central API
that all output goes through.

### Stage 01 — Terminal capability detection ✅

**Goals:**
- Create `tga.backup.terminal.Terminal` singleton that detects:
  - **isInteractive**: `System.console() != null` (false when piped to file)
  - **supportsAnsi**: check `isInteractive` + env vars (`TERM`, `NO_COLOR`, `FORCE_COLOR`, Windows detection)
  - **width**: use `stty size` on Unix / fallback to 120; re-detect on SIGWINCH if feasible, or just detect once at startup
  - **colorScheme**: detect via `COLORFGBG` env var or assume dark (most terminals are dark); provide `isDark`/`isLight`

**Acceptance criteria:**
- `Terminal.width` returns the actual column count on macOS
- `Terminal.isInteractive` returns `false` when stdout is redirected
- `Terminal.supportsAnsi` returns `false` when `NO_COLOR` is set
- Unit tests with mocked env/system properties

### Stage 02 — ANSI styling DSL ✅

**Goals:**
- Create `tga.backup.terminal.Style` — a small utility for styled text:
  - Color palette: define semantic colors (success, warning, error, info, muted, accent) with dark/light variants
  - `style(text, fg, bold, dim)` function that wraps text in ANSI codes only when `Terminal.supportsAnsi` is true
  - `truncateToWidth(text, maxWidth)` — truncate with ellipsis if text exceeds width (strips ANSI for measurement)
  - Icons: define Unicode icon constants — degrade to ASCII when ANSI is off
- No third-party library — keep it minimal (just string wrapping)

**Acceptance criteria:**
- `style("hello", Color.SUCCESS)` returns ANSI-wrapped string when supported, plain "hello" otherwise
- `truncateToWidth` correctly handles strings with embedded ANSI codes
- Icons degrade to ASCII equivalents

### Stage 03 — Throttled non-interactive output ✅

**Goals:**
- Enhance `ConsoleMultiThreadWorkers` to accept a `Terminal` reference
- When `!Terminal.isInteractive`:
  - Switch from cursor-movement output to simple line-by-line printing
  - **Throttle** per-thread status updates: first update prints immediately, then exponential backoff from 1s to 10s
  - **Always** print the last/final status of each task
  - Global status line follows the same throttling rules
- When `Terminal.isInteractive`:
  - Pass `Terminal.width` to the status callback (or use it internally to truncate)
  - Guard: if a status string (after ANSI-stripping) is longer than terminal width, truncate it — never wrap to the next line

**Acceptance criteria:**
- Piped output (`> file.txt`) produces clean, readable logs without escape codes
- Throttling works: in a 20-task demo, non-interactive mode produces far fewer lines than interactive
- In interactive mode, lines longer than terminal width are truncated, not wrapped
- Existing tests still pass

---

## Phase 2: Apply Styling to Existing Output

Now that the infrastructure exists, apply it across the codebase.

### Stage 04 — Summary table & plan styling ✅

**Goals:**
- Restyle `printSummary()`: use box-drawing chars, bold headers, alternating row colors (odd/even), right-aligned numbers
- Restyle warning messages (no-deletion, no-overriding) with warning icon and warning color
- Style the `Continue (Y/N/m)?>` prompt
- Style file lists (`logFilesList`, `logMovesList`): add icons, colored sizes, numbered with muted color

**Acceptance criteria:**
- Summary table renders with box-drawing characters and colored rows
- All styling degrades cleanly when `Terminal.supportsAnsi` is false (plain ASCII table, no escape codes)
- Visual check in terminal (run demo with `--dry-run`)

### Stage 05 — Phase headers, logos & logging ✅

**Goals:**
- Restyle `printLogo()` — add color/bold to the logo banner
- Style `logPhase()` / `logPhaseDuration()` — add icons, colors, bold phase name
- Style `logWrap()` — colored ok/error suffixes
- Style `loadTree()` output ("Listing Source files" etc.)
- Style the final summary (`printFinalSummary`) — green for success count, red for errors, error details in red

**Acceptance criteria:**
- All phase transitions are visually distinct with icons and color
- Error output stands out clearly
- Clean degradation in non-interactive mode

### Stage 06 — Multi-thread progress styling

**Goals:**
- Apply semantic colors to `ConsoleMultiThreadWorkers` output lines:
  - Thread status: use info/accent color, progress indicators
  - Global status line: bold, with progress bar or percentage in color
  - Error lines: red with error icon
- Ensure the progress display uses `Terminal.width` for alignment:
  - All thread lines should be the same width (padded/truncated)
  - Global status line centered or left-aligned consistently

**Acceptance criteria:**
- Multi-thread progress looks aligned and colored in interactive mode
- Non-interactive mode outputs throttled plain-text status
- Demo (`demo.kt`) showcases the new look

---

## Phase 3: Polish & Integration

### Stage 07 — End-to-end integration & polish

**Goals:**
- Run the full backup flow with `--dry-run` against test fixtures and verify output visually
- Fix any alignment issues, color clashes, or degradation bugs found
- Update `demo.kt` to showcase all rendering features
- Ensure `mvn test` passes with all changes

**Acceptance criteria:**
- Full dry-run produces visually polished output in interactive terminal
- Same run piped to file produces clean, readable plain text
- All existing tests pass
- New tests cover terminal detection and style utilities

### Stage 08 — Final verification & cleanup

**Goals:**
- Final review of all changes
- Clean up any TODO/FIXME left during development
- Verify no regressions in existing functionality
- Ensure code is well-organized under the new `tga.backup.terminal` package

**Acceptance criteria:**
- `mvn clean test` passes
- No leftover TODOs
- Code review findings addressed
