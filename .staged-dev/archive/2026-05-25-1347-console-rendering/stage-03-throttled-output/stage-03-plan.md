---
currentActivity: "All steps complete — dual output modes, throttling, width truncation, 6 tests passing"
nextPlannedActivity: "Stage complete — proceed to stage transition"
---

# Stage 03 — Throttled Non-Interactive Output

## Goals

Enhance `ConsoleMultiThreadWorkers` so it produces clean, readable output when stdout is
not a terminal (piped to file or redirected). In interactive mode, also add width-aware
truncation so long status lines never wrap and break the multi-line layout.

## Context from previous stages

### Terminal detection (Stage 01)
- `Terminal` object in `tga.backup.terminal` — singleton with lazy init
- `TerminalCapabilities` data class: `isInteractive`, `supportsAnsi`, `width`, `isDarkTheme`
- `TerminalDetector` with injectable deps for testing

### ANSI styling DSL (Stage 02)
- `style()` function wraps text in ANSI codes only when `supportsAnsi` is true
- `stripAnsi()` removes ANSI escape sequences from text
- `visibleLength()` returns visible character count (ignoring ANSI codes)
- `truncateToWidth()` truncates text preserving ANSI codes, appends "…"
- ESC character defined as `val ESC = 27.toChar()`

### Current ConsoleMultiThreadWorkers behavior
File: `src/main/kotlin/tga/backup/utils/ConsoleMultiThreadWorkers.kt`

- Constructor takes `threadCount: Int`
- `init` block prints `threadCount + 1` blank lines to reserve console space
- `outputStatus(lineIndex, status)` uses ANSI cursor movement (`ESC[nA`, `ESC[nB`) to
  update specific lines in-place
- `outputGlobalStatus(status)` updates the last line (1 line up from cursor)
- Both are `@Synchronized` / `synchronized(this)`
- No awareness of terminal width — long lines wrap and break the layout
- No throttling — every status update is printed immediately

### Existing tests
File: `src/test/kotlin/tga/backup/utils/ConsoleMultiThreadWorkersTest.kt`
- `demoTest` — 15 tasks with 5 workers, verifies results and error handling
- `submitDynamic` tests — verify child spawning, global status, error propagation
- Tests use hardcoded ANSI codes in updateStatus calls

## Step-by-step execution plan

### Step 1 — Add TerminalCapabilities to constructor

Add an optional `capabilities` parameter to `ConsoleMultiThreadWorkers`:

```kotlin
class ConsoleMultiThreadWorkers<T>(
    private val threadCount: Int,
    private val capabilities: TerminalCapabilities = TerminalDetector().detect(),
)
```

This allows tests to inject non-interactive capabilities without touching the real terminal.

### Step 2 — Implement dual output modes

Split `outputStatus` and `outputGlobalStatus` into two modes:

**Interactive mode** (`capabilities.isInteractive`):
- Keep current ANSI cursor-movement behavior
- Add width truncation: before printing a status line, call `truncateToWidth(status, capabilities.width)`
- This prevents line wrapping that breaks the multi-line layout

**Non-interactive mode** (`!capabilities.isInteractive`):
- Replace cursor-movement with simple `println`
- Strip ANSI codes from output using `stripAnsi()`
- Apply throttling (see Step 3)

The `init` block should only print blank lines in interactive mode.

### Step 3 — Implement throttling for non-interactive mode

For non-interactive output, throttle updates to avoid flooding log files:

- Track per-worker last-print timestamp in a `ConcurrentHashMap<Int, Long>`
- Track global-status last-print timestamp separately
- On each update:
  - If it's the first update for this worker, print immediately
  - Otherwise, apply exponential backoff: start at 1s, double each time, cap at 10s
  - Always print the final status when a task completes (detect via `finally` block)
- Implementation: add `shouldPrint(lineIndex)` method that checks elapsed time

### Step 4 — Update demo.kt

Update `demo.kt` to work well in both modes. Remove hardcoded ANSI codes and use
`style()` from the terminal package instead (or at minimum, ensure it runs cleanly
in non-interactive mode).

### Step 5 — Update existing tests

The existing `ConsoleMultiThreadWorkersTest` uses hardcoded ANSI codes. Update tests to:
- Pass explicit `TerminalCapabilities` to the constructor (non-interactive for test isolation)
- Add a test that verifies non-interactive output doesn't contain ANSI escape sequences
- Add a test that verifies throttling reduces output count

### Step 6 — Verify build

Run `mvn clean test` to ensure no compilation errors and all tests pass.

## Acceptance criteria

- [ ] Piped output produces clean, readable logs without ANSI escape codes
- [ ] Throttling works: non-interactive mode produces fewer lines than one-per-update
- [ ] In interactive mode, status lines longer than terminal width are truncated
- [ ] The `init` block doesn't print blank lines in non-interactive mode
- [ ] Existing tests still pass (updated to use TerminalCapabilities)
- [ ] `mvn clean test` passes
