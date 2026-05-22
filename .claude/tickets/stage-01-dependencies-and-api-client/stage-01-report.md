# Stage 01 — Report

## What was done
- Added 3 Maven dependencies for Google Drive API v3
- Created `GDriveClient` — low-level wrapper with OAuth2 Desktop app auth:
  - Token persistence via `FileDataStoreFactory` at configurable path
  - Auto-opens browser on first auth, auto-refreshes on subsequent runs
  - Full API coverage: list, metadata, create folder, upload (resumable), download, delete, move, rename
  - `resolvePathToId` — converts `path/like/this` to Google Drive folder IDs
- Created `GDriveResponseException` — wraps `GoogleJsonResponseException`

## Key insights
- Google SDK handles OAuth2 flow entirely (browser open, local server callback, token storage)
- Google Drive uses folder IDs, not paths — `resolvePathToId` bridges the gap
- `uploadFile` uses resumable upload by default (SDK handles chunking)
- Download progress requires knowing `totalSize` upfront (passed from caller)
- Move operation in GDrive requires both `addParents` and `removeParents`
