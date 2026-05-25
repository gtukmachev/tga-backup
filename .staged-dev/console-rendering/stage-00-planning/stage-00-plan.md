---
currentActivity: "Planning complete"
nextPlannedActivity: "Proceed to Stage 01 — Terminal capability detection"
---

# Stage 00 — Planning

## Report

Analyzed the entire codebase to understand all output points:

1. **ConsoleMultiThreadWorkers.kt** — ANSI cursor movement for multi-line progress display. No width detection. Hardcoded escape sequences.
2. **BackupScript.kt** — Hardcoded `[33m` for yellow warnings. `printSummary()` builds ASCII tables with `|` and `-`. `logMovesList()` also uses hardcoded yellow.
3. **logging.kt** — `logFilesList()`, `formatFileSize()`, `formatNumber()`, `logPhase()` — all plain text.
4. **logo.kt** — Plain text logo via logger.
5. **treeLoader.kt** — Plain `println("Listing $name files:")`.
6. **demo.kt** — Hardcoded ANSI color codes for progress visualization.
7. **FileOps.kt** — `SyncStatus.formatProgress()` builds global status string, plain text.

The plan is organized into 3 phases (8 stages total):
- **Phase 1 (Stages 01-03):** Terminal detection + ANSI styling DSL + throttled non-interactive mode
- **Phase 2 (Stages 04-06):** Apply styling to summary tables, phase headers, and multi-thread display
- **Phase 3 (Stages 07-08):** End-to-end integration test and final cleanup
