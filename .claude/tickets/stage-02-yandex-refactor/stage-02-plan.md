---
currentActivity: "Stage 02 complete — scanYandex() refactored to use ConsoleMultiThreadWorkers"
nextPlannedActivity: "Stage 03 — verify and polish"
---

# Stage 02 — Refactor YandexFileOps.scanYandex()

## Goals
Replace the ad-hoc threading (manual Executor + Phaser + synchronized printLock) in `scanYandex()`
with `ConsoleMultiThreadWorkers` using the new dynamic task API.

## Execution Plan

1. Replace the manual `Executors.newFixedThreadPool(20)` + `Phaser` + `printLock` with
   a `ConsoleMultiThreadWorkers<Unit>(20)` instance
2. Convert the nested `scan()` function to use `submitDynamic` — each directory becomes a
   dynamic task that spawns children for subdirectories
3. Global status line: show files found count + total size discovered
4. Per-thread status: show the folder path currently being fetched
5. Keep error handling via `dynamicError` (built into the framework now)
6. Replace `phaser.arriveAndAwaitAdvance()` with `workers.awaitDynamic()`
7. Remove the backspace-based `printFilesSize()` function

## Acceptance Criteria
- Builds cleanly (`mvn compile`)
- Scanning logic is functionally identical (same FileInfo results)
- Console shows per-thread folder paths and global file/size count
- All existing tests pass
