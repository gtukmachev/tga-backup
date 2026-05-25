---
currentActivity: "Ready to start"
nextPlannedActivity: "Style logPhase() and logPhaseDuration() in logging.kt"
---

# Stage 05 — Phase Headers, Logos & Logging

## Goals

Style the remaining unstyle output: phase transitions, logo banner, tree loading messages.
After this stage, every user-facing print statement in the codebase will go through the
styling infrastructure.

## Context from previous stages

### Already styled (Stage 04)
- `printSummary()` — box-drawing table with bold/colored rows
- Warning messages — `style()` + `Icons.WARNING`
- `logFilesList()` — file icons, muted numbers/sizes
- `logMovesList()` — `Icons.ARROW`, styled renamed marker
- `logWrap()` — green ok, red error suffixes
- `printFinalSummary()` — colored counts with icons
- `Continue` prompt — bold

### Still needs styling
1. `logPhase()` in `logging.kt:81-83` — currently `logger.warn { "[$timestamp] Phase: $phaseName" }`
2. `logPhaseDuration()` in `logging.kt:86-88` — currently `logger.warn { "Phase '$phaseName' completed in..." }`
3. `printLogo()` in `logo.kt:7-39` — plain text help banner via `log.info`
4. `loadTree()` in `treeLoader.kt:18` — `println("Listing $name files:")`

## Step-by-step execution plan

### Step 1 — Style `logPhase()` and `logPhaseDuration()`

In `logging.kt`:
- `logPhase()`: add an icon prefix and bold the phase name:
  `logger.warn { "[$timestamp] ${Icons.ARROW} Phase: ${style(phaseName, bold = true)}" }`
  Note: logger.warn goes through SLF4J which may strip ANSI. Consider using `println`
  instead for direct console output, or keep the logger call but also add a styled println.
  Actually — looking at how the code works, `logger.warn` goes to stderr via the logging
  framework. The styling should work on stderr too. Keep using `logger.warn` but add styling.
  
- `logPhaseDuration()`: add a checkmark and muted duration:
  `logger.warn { "${Icons.CHECK} Phase '$phaseName' completed in ${style(formatted, Color.MUTED)}" }`

### Step 2 — Style `printLogo()`

In `logo.kt`:
- Add `style()` to the title "Backup utility" — make it bold with ACCENT color
- Keep the rest as plain text (it's help text, readability > beauty)
- The `log.info` call goes through SLF4J — same consideration as Step 1

### Step 3 — Style `loadTree()` output

In `treeLoader.kt:18`:
- `println("\nListing $name files:")` → `println("\n${Icons.FOLDER} ${style("Listing $name files:", bold = true)}")`
- Add import for style, Icons

### Step 4 — Verify build

Run `mvn clean test` to ensure all tests pass.

## Acceptance criteria

- [ ] Phase transitions show icons and bold phase names
- [ ] Phase durations show checkmarks
- [ ] Logo title is styled
- [ ] Tree loading messages show folder icons
- [ ] All styling degrades cleanly when `Terminal.supportsAnsi` is false
- [ ] `mvn clean test` passes
