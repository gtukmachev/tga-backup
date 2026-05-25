---
currentActivity: "Ready to start"
nextPlannedActivity: "Run dry-run with safe test paths and verify visual output"
---

# Stage 07 — End-to-end Integration & Polish

## Goals

Run the full backup flow to verify all styled output works together. Fix any alignment
issues, color clashes, or degradation bugs found. Ensure the output is clean both in
interactive mode and when piped to a file.

## Context from previous stages

### What's been styled (Stages 01-06)
- Terminal detection: `Terminal` singleton, `TerminalDetector` (Stage 01)
- Styling DSL: `style()`, `Color`, `Icons`, `stripAnsi()`, `truncateToWidth()` (Stage 02)
- Throttled non-interactive output in `ConsoleMultiThreadWorkers` (Stage 03)
- Summary table with box-drawing chars, warnings, prompts, file/move lists (Stage 04)
- Phase headers with icons, logo title, tree loading messages (Stage 05)
- Multi-thread progress: StatusListener, SyncStatus, scan messages, demo.kt (Stage 06)

### Safety rules (from CLAUDE.md)
When running the program:
- ALWAYS use `--dry-run`
- Use safe paths: `-sr src/test/resources/source` `-dr target/test-destination`
- Pipe input to avoid hanging: `echo n | mvn exec:java ...`
- Add empty remote credentials: `-yu "" -yt ""`

## Step-by-step execution plan

### Step 1 — Run dry-run with local paths

```bash
echo n | mvn exec:java -Dexec.mainClass=tga.backup.MainKt \
  -Dexec.args="-sr src/test/resources/source -dr target/test-destination --dry-run -yu \"\" -yt \"\""
```

Check:
- Logo title is styled (accent + bold)
- Phase headers show arrow icon + bold phase name
- Phase durations show checkmark + muted time
- Tree loading shows folder icon + bold text
- Summary table has box-drawing characters
- Warning messages use warning icon/color (if applicable)
- Prompt is bold
- No broken ANSI sequences or misaligned columns

### Step 2 — Run dry-run piped to file (non-interactive mode)

```bash
echo n | mvn exec:java -Dexec.mainClass=tga.backup.MainKt \
  -Dexec.args="-sr src/test/resources/source -dr target/test-destination --dry-run -yu \"\" -yt \"\"" \
  > target/test-output.txt 2>&1
```

Check:
- No ANSI escape codes in the output file
- Output is readable plain text
- Icons degrade to ASCII equivalents

### Step 3 — Check for remaining hardcoded ANSI codes

```bash
grep -rn '\\u001b\|\\033\|\\x1b\|\\e\[' src/main/kotlin/ --include='*.kt'
```

Also check for actual ESC bytes:
```bash
python3 -c "import os; [print(f) for f in ... if ESC in content]"
```

Any remaining hardcoded ANSI codes should go through `style()`.

### Step 4 — Fix any issues found

Address alignment problems, color clashes, or degradation failures.

### Step 5 — Verify build

Run `mvn clean test` to ensure all tests pass.

## Acceptance criteria

- [ ] Full dry-run produces visually polished output in terminal
- [ ] Same run piped to file produces clean, readable plain text without ANSI codes
- [ ] No hardcoded ANSI escape codes remain (except in test files)
- [ ] All 126+ tests pass
- [ ] No alignment or rendering issues in the output
