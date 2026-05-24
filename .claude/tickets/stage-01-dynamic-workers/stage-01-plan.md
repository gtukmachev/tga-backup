---
currentActivity: "stage planning"
nextPlannedActivity: "Implement submitDynamic and awaitDynamic in ConsoleMultiThreadWorkers"
---

# Stage 01 — Extend ConsoleMultiThreadWorkers with Dynamic Task Support

## Goals
Add the ability for tasks to spawn child tasks dynamically, with the framework waiting until 
all dynamically-spawned work completes. This is needed because Yandex scanning discovers 
directories at runtime and spawns a new task for each one.

## Execution Plan

1. Add a `Phaser` field to `ConsoleMultiThreadWorkers` (lazy-initialized on first dynamic use)
2. Add `submitDynamic(task)` method:
   - Registers with the Phaser before submitting
   - The task receives a `submitChild` callback that can spawn more dynamic tasks
   - On completion (success or failure), deregisters from the Phaser
   - Returns `Future<Result<T>>` like `submit()`
3. Add `awaitDynamic()` method:
   - Calls `phaser.arriveAndAwaitAdvance()` (the caller is registered as the initial party)
   - Does NOT shut down the executor (unlike `waitForCompletion()`)
4. Add a convenience lambda overload for `submitDynamic`
5. Write a test that:
   - Creates dynamic tasks that spawn children (tree-like)
   - Verifies all tasks complete
   - Verifies status updates work on per-thread lines
6. Verify existing tests and demo still compile and work

## Acceptance Criteria
- `mvn clean test` passes
- Existing `submit()`/`waitForCompletion()` behavior unchanged
- New `submitDynamic()`/`awaitDynamic()` correctly waits for all spawned work
- Tasks can spawn children from within their execution
