# Stage 04 — Report

## What was done
- Fixed `pathToIdMap` in `GDriveFileOps` to store full paths (including root prefix like `backup/photos`)
  instead of relative-to-root paths (`photos`). This ensures consistent path resolution across
  `mkDirs`, `copyFile`, `deleteFileOrFolder`, and `moveFileOrFolder`.
- Implemented `GDriveFileOps.copyFile` with `Local -> GDrive` upload support:
  - Resolves destination path to parent folder ID via `pathToIdMap`
  - Uses `GDriveClient.uploadFile` with resumable upload
  - Reports progress via `StatusListener` (same pattern as `YandexFileOps`)
- Added `GDriveFileOps.downloadFile` method:
  - Resolves source path to file ID via `pathToIdMap`
  - Fetches file metadata to get total size (needed for progress reporting)
  - Downloads to output stream via `GDriveClient.downloadFile`
- Added `GDrive -> Local` download branch in `LocalFileOps.copyFile`:
  - `downloadFromGDrive` method mirrors `downloadFromYandex` pattern
  - Uses existing `LocalFileOps.StatusListener` for progress display

## Key insight
The `pathToIdMap` stores full paths (e.g., `backup/photos/file.jpg`) because the sync engine
constructs full paths like `gdrive://backup/photos/file.jpg` and strips only the `gdrive://`
prefix. Storing relative-to-root paths would have caused lookup misses in `mkDirs` and `copyFile`.
