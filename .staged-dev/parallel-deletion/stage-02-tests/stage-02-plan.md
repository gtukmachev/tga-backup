---
currentActivity: "Ready to start"
nextPlannedActivity: "Create test class with fake FileOps for parallel deletion"
---

# Stage 02 — Tests for Parallel Deletion

## Goals

Write unit tests for `FileOps.deleteFiles()` with the parallel workers path, verifying:
1. The leaf-safety invariant (folders deleted only after all children).
2. Files are all deleted (regardless of order — they're independent).
3. Global status reporting works (file count/size, folder count).
4. Edge cases: empty input, only files, only folders, single item.
5. Error handling: one deletion fails, others still proceed.

## Context from Stage 01

- **Implementation:** `src/main/kotlin/tga/backup/files/FileOps.kt` — method `deleteFiles()`.
- **When `workers != null`:** files submitted in parallel, folders processed by depth level (deepest first).
- **Depth calculation:** `name.count { ch -> ch == filesSeparator[0] }` — counts separator chars in path.
- **The sequential fallback** (`workers = null`) is the old behavior — no need to re-test it.
- **Test framework:** JUnit 5 + AssertJ. See existing tests in `src/test/kotlin/`.

## Execution Plan

1. **Create a `TestableFileOps` class** (in the test) that extends `FileOps`:
   - Override `deleteFileOrFolder(path)` to record the deletion timestamp/order.
   - Use a `ConcurrentLinkedQueue<String>` or similar to capture deletion order.
   - Optionally add a small `Thread.sleep()` in deletion to make ordering visible.
   - Override other abstract methods with no-ops.

2. **Test: files are all deleted** — submit a set of files, verify all paths appear in the
   recorded deletions.

3. **Test: folder depth ordering** — submit files + folders at various depths. Verify that
   for any two folders where one is an ancestor of the other, the descendant was deleted first.
   Use timestamps or sequence numbers to verify ordering.

4. **Test: global status shows correct counts** — capture the final global status string,
   verify it contains the correct file count, folder count, and total size.

5. **Test: empty input** — verify returns empty list, no interactions.

6. **Test: error in one deletion doesn't stop others** — make `deleteFileOrFolder` throw for
   one specific path. Verify the result contains one failure and the rest are successes.

7. **Run `mvn test`** to verify all pass.

## Acceptance Criteria

- [ ] At least 5 test cases covering the scenarios above.
- [ ] The depth-ordering invariant is explicitly verified (not just "it didn't crash").
- [ ] `mvn test` passes with all new and existing tests.
