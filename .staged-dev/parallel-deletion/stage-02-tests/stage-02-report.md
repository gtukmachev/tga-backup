# Stage 02 Report — Tests

## What was done

Created `DeleteFilesParallelTest.kt` with 8 test cases using a `TestableFileOps` fake that
records deletion paths and their sequence numbers via an `AtomicInteger`.

### Test coverage

1. `all files are deleted` — verifies all file paths appear in deletion log
2. `folders are deleted deepest first` — verifies ancestor/descendant ordering
3. `mixed files and folders - files deleted before folders` — verifies phase ordering
4. `empty input returns empty results` — edge case
5. `dry run does not call deleteFileOrFolder` — safety check
6. `error in one deletion does not stop others` — fault isolation
7. `noDeletion flag returns empty results` — parameter check
8. `only folders at different depths maintains ordering` — complex depth chain (4 levels)

## Plan review

The remaining stage (Stage 03 / originally Stage 04) is backend verification — checking that
`GDriveClient` and `YandexResumableUploader` are thread-safe for concurrent delete calls.

Looking at the implementations:
- `YandexFileOps.deleteFileOrFolder` calls `yandex.delete(path, notPermanently)` — a single
  HTTP call with no shared mutable state.
- `GDriveFileOps.deleteFileOrFolder` reads from `pathToIdMap` (populated during scan), then
  calls `gdrive.deleteFile(fileId)` — also a single HTTP call.

Both appear inherently thread-safe since they make independent HTTP requests with no shared
mutable writes. The `pathToIdMap` is read-only during deletion (populated during scan phase).

The next stage should confirm this by code inspection and potentially a dry-run test.
The plan still holds — one more stage to go.
