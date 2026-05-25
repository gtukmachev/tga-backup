# Stage 03 Report — Throttled Non-Interactive Output

## What was done

Enhanced `ConsoleMultiThreadWorkers` with terminal-aware output:

1. **Constructor change** — added optional `capabilities: TerminalCapabilities` parameter
   (defaults to live detection) so tests can inject non-interactive capabilities.

2. **Dual output modes:**
   - **Interactive**: keeps ANSI cursor movement, now truncates status lines to `capabilities.width`
   - **Non-interactive**: uses `println` with `stripAnsi()`, no cursor movement, no blank line init

3. **Throttling** — non-interactive mode uses per-worker exponential backoff (1s → 10s cap):
   - First update prints immediately
   - Subsequent updates throttled via `shouldPrint()` which tracks per-lineIndex timestamps
   - Global status has its own throttle state
   - Final status and error status always force-printed

4. **Test updates** — all existing tests now pass explicit `nonInteractive` capabilities.
   Two new tests:
   - Verify non-interactive output contains no ANSI escape codes
   - Verify throttling reduces line count below 1:1

## Design decisions

- `force: Boolean = false` parameter on `outputStatus` and `outputGlobalStatus` — cleaner
  than detecting "is this the last call" inside the method.
- Throttle state stored in `ConcurrentHashMap<Int, Long>` keyed by lineIndex — thread-safe
  without needing the `@Synchronized` lock.
- Kept ESC bytes as literal bytes in Kotlin string literals (from original code) rather than
  converting to `` — both work identically, avoids touching working ANSI code.

## Plan review

The remaining stages still make sense:
- Stage 04 (summary table styling) can now use `style()`, `Terminal`, and the infrastructure.
- Stages 05-06 can apply styling knowing the output modes are handled.
- Stage 07 (integration) should now include testing both interactive and non-interactive modes.
- No plan changes needed.
