---
currentActivity: "Implemented upload (Local->GDrive) and download (GDrive->Local) with progress reporting"
nextPlannedActivity: "Verify acceptance criteria and transition to Stage 05"
---

# Stage 04 — File Transfer (Upload & Download)

## Goals
Implement file copy operations between Local and Google Drive with progress reporting.

## Step-by-step execution plan

1. **Implement `GDriveFileOps.copyFile`** for `Local -> GDrive` (upload):
   - Resolve destination path to parent folder ID
   - Use `GDriveClient.uploadFile` with resumable upload
   - Integrate with `SyncStatus` and `StatusListener` for progress display
   - Mirror the `YandexFileOps.StatusListener` pattern

2. **Add download support to `GDriveFileOps`**:
   - Add `downloadFile` method that wraps `GDriveClient.downloadFile`
   - Handles file ID resolution from path

3. **Implement `LocalFileOps.copyFile`** branch for `GDrive -> Local` (download):
   - Add `is GDriveFileOps` branch in `LocalFileOps.copyFile`
   - Download to local file with progress reporting
   - Mirror the existing `downloadFromYandex` pattern

4. **Update `CopyDirectionIsNotSupportedYet`** message

5. **Verify** compilation and tests

## Acceptance criteria
- `GDriveFileOps.copyFile` handles Local -> GDrive uploads with progress
- `LocalFileOps.copyFile` handles GDrive -> Local downloads with progress
- Progress display works with `SyncStatus` (percentage, speed, ETA)
- Project compiles, all tests pass
