---
currentActivity: "stage planning"
nextPlannedActivity: "Create GDriveFileOps class extending FileOps"
---

# Stage 02 — GDriveFileOps Implementation

## Goals
Create `GDriveFileOps` extending `FileOps` — the main integration point between
the backup engine and Google Drive, handling scanning, folder creation, deletion,
and move/rename operations. File transfer (copy) is deferred to Stage 04.

## Key design considerations
- Google Drive uses **file IDs**, not paths. The `GDriveClient.resolvePathToId`
  method bridges this gap, but `GDriveFileOps` needs to maintain an internal
  path-to-ID mapping during operations.
- Google Drive allows **multiple files with the same name** in the same folder.
  During scanning, we should handle this gracefully.
- The `FileInfo.name` field stores the **relative path** from root (same as Yandex).

## Step-by-step execution plan

1. **Create `GDriveFileOps`** in `tga.backup.files` package:
   - Constructor: takes `GDriveClient`, `profile`, `useCache`, `excludePatterns`
   - `filesSeparator = "/"`

2. **Implement `getFilesSet`**:
   - Resolve `gdrive://path` to folder ID via `GDriveClient.resolvePathToId`
   - Recursively list all files/folders using `GDriveClient.listFiles` with pagination
   - Map Google Drive entries to `FileInfo` (name=relative path, size, md5 from metadata)
   - Support remote cache (reuse `RemoteCache.kt`)
   - Use concurrent scanning with `Phaser` + thread pool (same pattern as `YandexFileOps`)

3. **Implement `mkDirs`**:
   - Create folders via `GDriveClient.createFolder`
   - Track created folders to avoid duplicates (like `YandexFileOps.createdFolders`)
   - Handle path-to-ID resolution for nested folder creation

4. **Implement `deleteFileOrFolder`**:
   - Resolve path to file ID, then call `GDriveClient.deleteFile`

5. **Implement `moveFileOrFolder`**:
   - Resolve source and destination paths to IDs
   - Use `GDriveClient.moveFile` or `renameFile`

6. **Implement `close`**:
   - Delegate to `GDriveClient.close()`

7. **Implement `copyFile`** (stub for now):
   - Throw `CopyDirectionIsNotSupportedYet` — actual transfer is Stage 04

8. **Verify** compilation and tests

## Acceptance criteria
- `GDriveFileOps` extends `FileOps` with all abstract methods implemented
- `getFilesSet` correctly maps Google Drive structure to `FileInfo` set
- Remote cache is wired up
- `mkDirs` creates nested folders via API
- `deleteFileOrFolder` and `moveFileOrFolder` work with path-to-ID resolution
- Project compiles, all tests pass
