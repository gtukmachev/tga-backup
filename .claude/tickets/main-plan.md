---
currentStage: 01-dynamic-workers
currentStagePath: .claude/tickets/stage-01-dynamic-workers
---

# Refactor Yandex Scanning to Use ConsoleMultiThreadWorkers

## Goal

Replace the ad-hoc threading and backspace-based progress display in `YandexFileOps.scanYandex()` 
with the project's standard `ConsoleMultiThreadWorkers` framework. This gives Yandex scanning 
the same clean multi-line console UI used during file copying: per-thread status lines showing 
which folder each worker is currently fetching, and a global status line showing overall scan 
progress (file count + total size).

## Current State

- `scanYandex()` uses its own `Executors.newFixedThreadPool(20)` + `Phaser` for parallel 
  directory scanning
- Progress is a crude backspace-based file count on a single line
- `ConsoleMultiThreadWorkers` doesn't support dynamic task spawning (tasks that create sub-tasks)
  — it calls `executor.shutdown()` in `waitForCompletion()`, blocking new submissions

## Stages

### Stage 01 — Extend ConsoleMultiThreadWorkers with dynamic task support
- Add Phaser-based `submitDynamic()` method that allows tasks to spawn child tasks
- Add `awaitDynamic()` that waits on the Phaser instead of shutting down the executor
- Keep existing `submit()`/`waitForCompletion()` unchanged (backward compatible)
- Write a unit test verifying dynamic tasks work correctly
- **Acceptance:** existing demo and tests still pass; new test for dynamic spawning passes

### Stage 02 — Refactor YandexFileOps.scanYandex() to use ConsoleMultiThreadWorkers
- Replace the manual executor+Phaser+printLock code with ConsoleMultiThreadWorkers
- Each directory scan becomes a dynamic task reporting its folder path via `updateStatus`
- Global status line shows: files found count + total size discovered so far
- Thread count becomes configurable (default 20, could tie to params later)
- **Acceptance:** builds cleanly; scanning logic is functionally identical; console output 
  shows per-thread folder paths and global file count

### Stage 03 — Verify and polish
- Build the project and run all tests
- Dry-run with safe test paths to verify console output looks correct
- Clean up any unused imports or dead code from the old approach
- **Acceptance:** `mvn clean test` passes; dry-run produces clean multi-line output
