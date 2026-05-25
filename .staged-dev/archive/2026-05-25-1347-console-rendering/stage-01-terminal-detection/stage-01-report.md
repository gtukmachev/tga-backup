# Stage 01 Report — Terminal Capability Detection

## What was done

Created the `tga.backup.terminal` package with two files:

1. **`TerminalDetector.kt`** — Contains `TerminalDetector` class with injectable dependencies
   (`getEnv`, `getConsole`, `runCommand`) for testability, and `TerminalCapabilities` data class.
   Detection logic:
   - `isInteractive`: delegates to `System.console()`
   - `supportsAnsi`: checks `FORCE_COLOR` (overrides), `NO_COLOR`, `TERM=dumb`, OS-level Windows check
   - `width`: tries `stty size` (interactive only) → `COLUMNS` env → fallback 120, clamped min 40
   - `isDarkTheme`: parses `COLORFGBG` last segment, defaults to dark

2. **`Terminal.kt`** — Singleton object that delegates to a default `TerminalDetector` at startup.

3. **`TerminalDetectorTest.kt`** — 20 tests across 4 nested groups covering all branches.

## Design decisions

- `FORCE_COLOR` takes priority over `NO_COLOR` but still requires `isInteractive` — this follows
  the principle that piped output shouldn't get escape codes unless there's a tty.
- `stty size` is only attempted when interactive — avoids process spawn overhead in piped mode.
- Width minimum is 40 — anything smaller would break table formatting in later stages.
- The `execCommand` helper is a private function (not part of the class API) — it's the default
  for `runCommand` but tests inject their own.

## Plan review

The remaining stages still make sense. Key observations:
- Stage 02 (ANSI styling DSL) can directly use `Terminal.supportsAnsi` and `Terminal.isDarkTheme`
  as designed.
- Stage 03 (throttled output) can use `Terminal.isInteractive` and `Terminal.width` as planned.
- No unexpected complexity was found — the detection logic was straightforward.
- The plan holds as-is; no changes needed.
