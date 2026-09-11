# Bug: Delete phase destroys files scheduled for move

## Summary

When the user answers "Y" (full sync), the execution order is Copy → Override → **Delete → Move**. 
The delete phase deletes a **parent folder** that still contains files scheduled for move, causing
cascading deletion of those files. The subsequent move phase then fails with 404 NOT FOUND.

## Root Cause

Two interacting issues:

### 1. Parent folder stays in the delete list despite containing move-source files

In `filesComparator.kt:56-73`, individual files matched by MD5+size are correctly removed from
`toDeleteFiles` and added to `toMoveFiles`. However, their **parent folder** remains in the
delete list when the folder-level move detection (lines 92-153) fails — which happens when
NOT ALL files in the folder are moved (some are genuinely deleted, e.g. `.JPG` files that
don't exist in source).

The folder `_new/2026/2026-09-10` stays in `toDeleteFiles` because the folder-move check
at line 118 requires ALL non-excluded files to be accounted for, which isn't the case here
(only `.CR3` files match; `.JPG` files are truly orphaned).

### 2. Delete runs before Move

In `BackupScript.kt:118-130`, the execution order is:
```
line 118: Delete phase  ← runs first
line 125: Move phase    ← runs second
```

When the delete phase executes, it deletes the folder `_new/2026/2026-09-10` on Yandex Disk.
Yandex recursively removes the folder and ALL its contents — including the `.CR3` files that
were individually excluded from the delete list but physically still reside inside that folder.

## Observed Behavior (from real run on 2026-09-11)

```
Deleting: 40/40 files (468 m) | 1/1 folders     ← folder deleted, taking .CR3 files with it
...
Moving: ...r6-...-0017.MP4  →  ...  Response code: [423] LOCKED    ← Yandex still processing delete
Moving: ...r6-...-0002.CR3  →  ...  Response code: [404] NOT FOUND ← file already gone
... (22 more 404 errors)
```

24 files that should have been moved were destroyed instead.

## Proposed Fix

**Move the Move/Rename phase before the Delete phase** in `BackupScript.kt`:

```kotlin
// Current order (broken):
//   1. Copy
//   2. Override
//   3. Delete    ← destroys move sources
//   4. Move

// Fixed order:
//   1. Copy
//   2. Override
//   3. Move      ← files relocated safely first
//   4. Delete    ← now safe to clean up
```

This is the minimal, safest fix because:
- Moves relocate existing remote files — no new uploads needed, just metadata operations
- After moves complete, the moved files are no longer in the folder being deleted
- The folder (now empty of move-source files) can be safely deleted
- The "m" option already proves moves work independently

### Alternative / additional fix

Also exclude parent folders from `toDeleteFiles` if they contain move-source children.
This is a belt-and-suspenders approach in `filesComparator.kt` — worth considering but
the reorder alone resolves the immediate data loss.

## Key Code References

| File | Lines | What |
|------|-------|------|
| `BackupScript.kt` | 118-130 | Execution order: delete before move |
| `filesComparator.kt` | 56-73 | File-level move detection (removes files from delete list) |
| `filesComparator.kt` | 92-153 | Folder-level move detection (requires ALL files to match) |
| `filesComparator.kt` | 84 | `toDeleteFolders` extracted from `toDeleteFiles` |
