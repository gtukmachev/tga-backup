---
currentStage: 01-dependencies-and-api-client
currentStagePath: .claude/tickets/stage-01-dependencies-and-api-client
---

# Google Drive Integration (`gdrive://`)

## Goal
Add Google Drive as a third storage provider alongside Local and Yandex Disk.
Users will be able to use `gdrive://` URLs as source or destination, enabling
backups to/from Google Drive.

## Architecture Decisions

- **Google Drive API v3** via official `google-api-services-drive` Java SDK
- **Authentication**: OAuth2 with a Service Account JSON key file (simplest for CLI backup tool).
  The path to the key file is passed via a new `-gk` / `--gdrive-key` parameter.
  Optionally, a Shared Drive ID can be specified via `-gd` / `--gdrive-drive-id`.
- **URL scheme**: `gdrive://path/to/folder` (mirrors `yandex://` pattern)
- **HTTP client**: Google SDK provides its own HTTP transport; no need to reuse OkHttp
- **Resumable uploads**: Google SDK supports resumable media uploads natively
- **File separator**: `/` (same as Yandex)
- **MD5**: Google Drive provides MD5 checksums in file metadata (perfect for comparison)
- **Remote cache**: Reuse existing `RemoteCache.kt` infrastructure (same format)

## Stages

### Stage 01 — Dependencies & Google Drive API Client
- Add `google-api-services-drive`, `google-auth-library-oauth2-http`, and
  `google-api-client` Maven dependencies
- Create `tga.backup.gdrive.GDriveClient` — a low-level wrapper around the Drive API
  (list files, create folder, upload, download, delete, move)
- Implement Service Account authentication from a JSON key file
- Write unit tests for the client (mocked or integration against a test drive)
- **Acceptance**: Project compiles, `GDriveClient` can authenticate and list files in a test folder

### Stage 02 — GDriveFileOps Implementation
- Create `tga.backup.files.GDriveFileOps` extending `FileOps`
- Implement `getFilesSet` — recursive listing with pagination, mapping to `FileInfo`
  (using MD5 from Google Drive metadata)
- Implement `mkDirs` — create folders via API, tracking created paths
- Implement `deleteFileOrFolder` — delete by file ID
- Implement `moveFileOrFolder` — move/rename via parent change or name update
- Implement `close` — cleanup resources
- Wire up remote cache support (reuse `RemoteCache.kt`)
- **Acceptance**: `GDriveFileOps.getFilesSet` returns correct `FileInfo` set for a known folder structure

### Stage 03 — File Transfer (Upload & Download)
- Implement `GDriveFileOps.copyFile` for `Local -> GDrive` (upload with progress reporting)
- Implement `LocalFileOps.copyFile` branch for `GDrive -> Local` (download with progress)
- Use Google SDK resumable upload for large files
- Integrate with `SyncStatus` and progress callbacks (same pattern as Yandex)
- **Acceptance**: Files can be uploaded to and downloaded from Google Drive with progress display

### Stage 04 — Parameters & Wiring
- Add `gdrive-key` and `gdrive-drive-id` fields to `Params`
- Add CLI argument parsing: `-gk` / `--gdrive-key`, `-gd` / `--gdrive-drive-id`
- Update `builder.kt` to route `gdrive://` to `GDriveFileOps`
- Update `application.conf` defaults
- **Acceptance**: `./backup -sr /local/path -dr gdrive://backup/photos -gk /path/to/key.json --dry-run` parses correctly and creates the right FileOps

### Stage 05 — Integration Testing & Web Links
- Implement `generateWebLink` for Google Drive paths
- Add integration test with dry-run against a real or simulated Google Drive
- Test all copy directions: Local->GDrive, GDrive->Local
- Test edge cases: empty folders, deeply nested paths, special characters in names
- Update project documentation (guidelines, README if exists)
- **Acceptance**: Full backup dry-run completes with `gdrive://` destination; web links work

### Stage 06 — Polish & Error Handling
- Google-specific error handling (rate limiting, quota exceeded, auth failures)
- Retry logic for transient errors (503, network timeouts)
- Handle Google Drive-specific quirks (files with same name in same folder,
  Google Docs/Sheets files that can't be downloaded directly)
- Final testing and cleanup
- **Acceptance**: Error scenarios are handled gracefully with meaningful messages
