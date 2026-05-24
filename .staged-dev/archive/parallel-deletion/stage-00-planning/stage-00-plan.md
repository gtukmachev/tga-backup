---
currentActivity: "Planning complete"
nextPlannedActivity: "Start Stage 01 — Core implementation in FileOps"
---

# Stage 00 — Planning

## Summary

Analyzed the current deletion implementation in `FileOps.deleteFiles()` (line 66 of FileOps.kt).
The method iterates files in descending name order (deepest paths first) and deletes sequentially.

## Key findings

1. `FileInfo.compareTo` sorts by `name` — so `sortedDescending()` gives deepest paths first.
2. The `ConsoleMultiThreadWorkers` framework supports both `submit` (fixed task list) and
   `submitDynamic` (tree-recursive spawning with `awaitDynamic()`).
3. For deletion, `submit` is the right fit — we know all items upfront and just need to
   respect ordering constraints.
4. Both `YandexFileOps` and `GDriveFileOps` override `deleteFileOrFolder` — both are simple
   API calls that should be thread-safe (one HTTP call per item).
5. `GDriveFileOps` uses `pathToIdMap` to resolve file IDs — reads from this map during
   deletion are safe since scanning has already populated it.

## Plan review

The 4-stage plan covers: core implementation, caller integration, tests, and backend verification.
This gives good separation of concerns and each stage is small enough to complete in one session.
