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
- **Authentication**: OAuth2 user credentials (Desktop app flow). On first run,
  opens a browser for Google login; stores refresh token locally at
  `~/.tga-backup/<profile>/gdrive-token/`. Auto-refreshes on subsequent runs.
  User provides `client_secret.json` path via `-gc` / `--gdrive-credentials`.
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
- Implement OAuth2 Desktop app authentication with token persistence
- **Acceptance**: Project compiles, `GDriveClient` has all required methods

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

### Stage 03 — Google Cloud Project Setup (Browser via Playwright MCP)
- Use Playwright MCP tools to automate browser interaction with Google Cloud Console
- User signs in manually, then the agent drives the UI:
  - Create a GCP project (or select existing)
  - Enable the Google Drive API
  - Configure OAuth consent screen (External, test mode)
  - Create OAuth2 Desktop app credentials
  - Download `client_secret.json` to project directory
- **Acceptance**: `client_secret.json` exists and contains valid OAuth2 client credentials

### Stage 04 — File Transfer (Upload & Download)
- Implement `GDriveFileOps.copyFile` for `Local -> GDrive` (upload with progress reporting)
- Implement `LocalFileOps.copyFile` branch for `GDrive -> Local` (download with progress)
- Use Google SDK resumable upload for large files
- Integrate with `SyncStatus` and progress callbacks (same pattern as Yandex)
- **Acceptance**: Files can be uploaded to and downloaded from Google Drive with progress display

### Stage 05 — Parameters & Wiring
- Add `gdriveCredentials` field to `Params`
- Add CLI argument parsing: `-gc` / `--gdrive-credentials`
- Update `builder.kt` to route `gdrive://` to `GDriveFileOps`
- Update `application.conf` defaults
- **Acceptance**: `./backup -sr /local/path -dr gdrive://backup/photos -gc /path/to/client_secret.json --dry-run` parses correctly and creates the right FileOps

### Stage 06 — Integration Testing & Web Links
- Implement `generateWebLink` for Google Drive paths
- Run first real auth flow (browser login) using the `client_secret.json` from Stage 03
- Test all copy directions: Local->GDrive, GDrive->Local
- Test edge cases: empty folders, deeply nested paths, special characters in names
- Update project documentation (guidelines, README if exists)
- **Acceptance**: Full backup dry-run completes with `gdrive://` destination; web links work

### Stage 07 — Polish & Error Handling
- Google-specific error handling (rate limiting, quota exceeded, auth failures)
- Retry logic for transient errors (503, network timeouts)
- Handle Google Drive-specific quirks (files with same name in same folder,
  Google Docs/Sheets files that can't be downloaded directly)
- Final testing and cleanup
- **Acceptance**: Error scenarios are handled gracefully with meaningful messages
