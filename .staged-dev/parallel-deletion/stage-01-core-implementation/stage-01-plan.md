---
currentActivity: "Core implementation complete — FileOps.deleteFiles with workers, BackupScript updated"
nextPlannedActivity: "Stage complete — proceed to tests"
---

# Stage 01 — Core Implementation in FileOps

## Goals

Replace the sequential `deleteFiles` method in `FileOps` with a parallel version using
`ConsoleMultiThreadWorkers`. Maintain the safety invariant: folders are deleted only after
all their children.

## Context

- **File:** `src/main/kotlin/tga/backup/files/FileOps.kt`, line 66
- **Current behavior:** Sorts items descending by name (deepest first), iterates sequentially,
  calls `deleteFileOrFolder(path)` for each.
- **Framework:** `ConsoleMultiThreadWorkers<T>` — see `src/main/kotlin/tga/backup/utils/ConsoleMultiThreadWorkers.kt`
- **Pattern to follow:** See `copyFiles()` in the same `FileOps.kt` (lines 38-64) — it submits
  tasks to workers, uses `SyncStatus` for global progress, and calls `waitForCompletion()`.

## Execution Plan

1. **Add a new `deleteFiles` overload** that accepts a `ConsoleMultiThreadWorkers<Unit>` parameter.
   Keep the old signature as a delegate (creates workers internally) for backward compatibility
   during transition.

2. **Separate items by type:**
   - `filesToDelete` = items where `!isDirectory`, sorted descending by name
   - `foldersToDelete` = items where `isDirectory`, grouped by depth (number of separators in name),
     sorted from deepest to shallowest

3. **Phase 1 — Delete files in parallel:**
   - Submit all file deletions to workers.
   - Track progress: `AtomicLong` for deleted file count, `AtomicLong` for deleted size.
   - Update global status line: `"Deleting: X files (Y size) | Z folders"`

4. **Phase 2 — Delete folders level by level:**
   - Group folders by depth (count `/` in name).
   - For each depth level (deepest first):
     - Submit all folders at that depth to a NEW workers instance (or reuse with fresh executor).
     - Wait for completion before proceeding to the next level.
   - Track folder count in the global status.

   **Design choice:** Since `ConsoleMultiThreadWorkers` uses `executor.shutdown()` in
   `waitForCompletion()`, we need to either:
   - (a) Create a new workers instance per depth level, or
   - (b) Use `submit` + `Future.get()` without calling `waitForCompletion()` until the very end,
     processing depth levels sequentially by calling `.get()` on each level's futures.
   
   Option (b) is better — single workers instance, submit one level at a time, collect futures,
   call `.get()` on all of them before submitting the next level. Call `waitForCompletion()` only
   at the very end.
   
   **Wait — `waitForCompletion()` calls `executor.shutdown()`.** So we can't reuse the instance.
   Instead: submit all files, then for each folder level, submit and `.get()` all futures for
   that level. At the very end, call `waitForCompletion()`.

5. **Global status format:**
   ```
   Deleting: 42 files (1.5 GB) | 7 folders
   ```

6. **Return `List<Result<Unit>>`** — same contract as current implementation.

7. **Update the method signature in the base class** — the old signature (without workers)
   becomes a convenience wrapper that creates a small workers pool internally.

## Acceptance Criteria

- [ ] New `deleteFiles` method compiles and uses `ConsoleMultiThreadWorkers`.
- [ ] Files (non-directories) are deleted in parallel.
- [ ] Folders are deleted depth-level by depth-level (deepest first), each level in parallel.
- [ ] Global status line shows file count, file size, and folder count.
- [ ] Old callers still work (backward-compatible signature or updated).
- [ ] `mvn compile` succeeds.
- [ ] Existing tests pass (`mvn test`).
