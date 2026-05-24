---
currentActivity: "Starting GDrive scan refactoring"
nextPlannedActivity: "Replace manual executor+Phaser+printLock with ConsoleMultiThreadWorkers.submitDynamic"
---

# Stage 04 — Refactor GDriveFileOps.scanGDrive() to use ConsoleMultiThreadWorkers

## Goals
Apply the same refactoring done to YandexFileOps in Stage 02 to GDriveFileOps.scanGDrive().
Replace the ad-hoc Executor+Phaser+printLock with submitDynamic/awaitDynamic.

## Execution Plan

1. Replace imports: remove Executors/Phaser/AtomicReference, add ConsoleMultiThreadWorkers/DynamicTask/AtomicLong
2. Replace the manual thread pool + Phaser + printLock with ConsoleMultiThreadWorkers(20)
3. Convert the nested `scan()` function to `scanFolder()` using submitDynamic
4. Global status: "Scanning GDrive: N files [size]"
5. Per-thread status: "Fetching: /relative/folder/path"
6. Preserve pathToIdMap population (already ConcurrentHashMap — thread-safe)
7. Keep rootFolderId resolution before the workers start (it's a single synchronous call)
8. Run `mvn test` to verify

## Acceptance Criteria
- Builds cleanly
- All 73+ tests pass
- scanGDrive uses ConsoleMultiThreadWorkers instead of manual threading
