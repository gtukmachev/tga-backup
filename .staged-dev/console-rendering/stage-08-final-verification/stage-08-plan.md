---
currentActivity: "All checks pass — no TODOs, clean package structure, 126 tests pass"
nextPlannedActivity: "Stage complete — this is the final stage"
---

# Stage 08 — Final Verification & Cleanup

## Goals

Final review of all changes made during the console-rendering feature. Clean up any
leftover artifacts, verify code organization, and ensure all tests pass.

## Context from previous stages

All styling work is complete (Stages 01-06) and verified end-to-end (Stage 07):
- Terminal detection: `tga.backup.terminal.Terminal`, `TerminalDetector`
- Styling DSL: `style()`, `Color`, `Icons`, `stripAnsi()`, `truncateToWidth()`
- Throttled non-interactive output in `ConsoleMultiThreadWorkers`
- All user-facing output goes through `style()`, degrades cleanly
- No hardcoded ANSI codes remain outside infrastructure files
- 126 tests pass, dry-run output verified

## Step-by-step execution plan

### Step 1 — Scan for TODO/FIXME

```bash
grep -rn 'TODO\|FIXME\|HACK\|XXX' src/main/kotlin/ --include='*.kt'
```

Remove any leftover development notes that should not be shipped.

### Step 2 — Verify code organization

Check that the `tga.backup.terminal` package contains all styling infrastructure:
- `Terminal.kt` — singleton
- `TerminalDetector.kt` — detection logic
- `Style.kt` — `style()`, `Color`, `Icons`, helpers

Verify no styling logic leaked into other packages.

### Step 3 — Final test run

```bash
mvn clean test
```

All tests must pass.

### Step 4 — Review commit history

Verify the branch has a clean commit history with meaningful messages.

## Acceptance criteria

- [ ] No leftover TODO/FIXME from this feature
- [ ] Code is well-organized under `tga.backup.terminal`
- [ ] `mvn clean test` passes
- [ ] Clean commit history
