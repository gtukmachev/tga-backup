---
currentStage: 01-core-implementation
currentStagePath: .staged-dev/parallel-deletion/stage-01-core-implementation
codeReviewTool: none
---

# Parallel Deletion Phase

## Goal

Replace the single-threaded deletion phase in `FileOps.deleteFiles()` with a parallel
implementation using `ConsoleMultiThreadWorkers`, while preserving the safety invariant:
**folders must only be deleted after all their children have been deleted**.

The global status line should show: files deleted (count + size) and folders deleted (count).

Both `YandexFileOps` and `GDriveFileOps` must work correctly with the new approach.

## Key Constraint

The current implementation sorts files in **descending** name order (`sortedDescending()`) which
ensures deeper paths are deleted before shallower ones. In a parallel approach, we cannot simply
submit all items to a thread pool because a folder might be deleted while its children are still
being processed.

**Solution approach:** Use a two-phase strategy:
1. Delete all **files** (non-directories) in parallel — they have no dependencies.
2. Delete **folders** in leaf-first order — group by depth, process each depth level in parallel,
   wait for completion before moving to the next (shallower) level.

## Stages

### Stage 01 — Core implementation in FileOps

**Goals:**
- Implement the new `deleteFiles` method in `FileOps` that uses `ConsoleMultiThreadWorkers`.
- Phase 1: delete files in parallel with progress reporting.
- Phase 2: delete folders level-by-level (deepest first), each level in parallel.
- Global status line shows: deleted files count + size, deleted folders count.
- The method signature changes to accept a `ConsoleMultiThreadWorkers` parameter (like `copyFiles`).

**Acceptance criteria:**
- `deleteFiles` uses workers for parallelism.
- Leaf-safety invariant is preserved (folders deleted only after children).
- Global status line updates during deletion.
- Compiles and existing tests pass.

### Stage 02 — Update BackupScript and callers

**Goals:**
- Update `BackupScript.runDeleting()` to create and pass a `ConsoleMultiThreadWorkers` instance.
- Pass `params.parallelThreads` to the workers.
- Ensure the console output formatting is clean (blank lines for worker area, etc.).

**Acceptance criteria:**
- Deletion uses parallel workers when run from BackupScript.
- Console output is well-formatted (no garbled lines).
- `--dry-run` still works (no actual deletions).

### Stage 03 — Tests

**Goals:**
- Write unit tests for the new deletion logic:
  - Files are deleted in parallel (verify all submitted).
  - Folders are deleted only after their children (depth-level ordering).
  - Global status is updated with file count/size and folder count.
  - Empty input produces empty results.
  - Mixed files and folders are handled correctly.
- Test with mock/fake `FileOps` (don't need real Yandex/GDrive).

**Acceptance criteria:**
- Tests cover the core invariant (leaf-first folder deletion).
- Tests verify progress reporting.
- All tests pass (`mvn test`).

### Stage 04 — Verify with GDrive and Yandex specifics

**Goals:**
- Verify that `GDriveFileOps.deleteFileOrFolder` and `YandexFileOps.deleteFileOrFolder` are
  thread-safe (they call their respective clients).
- Check that `GDriveClient` and `YandexResumableUploader` handle concurrent delete calls.
- Add synchronization or adjustments if needed.
- Run integration-style tests or manual dry-run verification.

**Acceptance criteria:**
- Both backends work correctly with parallel deletion.
- No race conditions in path-to-ID resolution (GDrive) or API calls.
- Manual dry-run confirms correct plan output.
