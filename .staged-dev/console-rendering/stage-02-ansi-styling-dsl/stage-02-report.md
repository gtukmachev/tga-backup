# Stage 02 Report — ANSI Styling DSL

## What was done

Created `tga.backup.terminal.Style.kt` with:

1. **`Color` enum** — 6 semantic colors (SUCCESS, WARNING, ERROR, INFO, MUTED, ACCENT), each
   with dark and light theme ANSI codes.

2. **`style()` function** — wraps text in ANSI color/bold/dim codes. Accepts `supportsAnsi`
   and `isDarkTheme` parameters (defaulting to `Terminal` values) for testability. Returns
   plain text when ANSI is not supported or no attributes are requested.

3. **`stripAnsi()` / `visibleLength()`** — regex-based ANSI stripping and visible-character
   counting. The regex matches `ESC[...m` sequences.

4. **`truncateToWidth()`** — character-by-character walker that skips ANSI sequences when
   counting visible width. Appends "…" and a reset code at the truncation point.

5. **`Icons` object** — 8 icon constants (CHECK, CROSS, WARNING, INFO, ARROW, BULLET, FOLDER,
   FILE) with Unicode/ASCII fallback. Uses a mutable `supportsAnsi` property for testability.

6. **`StyleTest.kt`** — 20 tests across 5 nested groups covering all branches.

## Design decisions

- ESC character defined as `27.toChar()` — avoids encoding issues with `` in source files
  across different tools. The ANSI_REGEX has the actual ESC byte embedded (works correctly).
- `style()` takes `supportsAnsi`/`isDarkTheme` as parameters rather than reading `Terminal`
  globally — makes tests completely deterministic without touching `Terminal` state.
- `Icons.supportsAnsi` is a mutable `var` with `internal set` — allows tests to override it
  while keeping it read-only from outside the module.
- `truncateToWidth` appends `ESC[0m` reset after truncation to ensure no color leak.

## Plan review

The remaining stages still make sense:
- Stage 03 (throttled output) can now use `style()`, `truncateToWidth()`, and `Terminal`
  properties as designed.
- Stages 04-06 (applying styling to existing output) can use the `Color` enum and `Icons`
  object directly.
- No plan changes needed.
