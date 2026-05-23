---
currentActivity: "All goals complete: Maven dependencies added, GDriveClient and GDriveResponseException created, project compiles, all tests pass"
nextPlannedActivity: "Transition to Stage 02 — GDriveFileOps Implementation"
---

# Stage 01 — Dependencies & Google Drive API Client

## Goals
Create the low-level Google Drive API client that will be used by `GDriveFileOps`.

## Step-by-step execution plan

1. **Add Maven dependencies** to `pom.xml`:
   - `google-api-services-drive` v3-rev20260428-2.0.0
   - `google-api-client` 2.8.0
   - `google-oauth-client-jetty` 1.39.0

2. **Create `tga.backup.gdrive.GDriveClient`** — low-level wrapper:
   - OAuth2 Desktop app authentication with token persistence
   - `listFiles`, `getFileMetadata`, `createFolder`, `uploadFile`, `downloadFile`
   - `deleteFile`, `moveFile`, `renameFile`, `resolvePathToId`
   - `close()`

3. **Create `tga.backup.gdrive.GDriveResponseException`** — custom exception

4. **Verify** compilation and tests

## Acceptance criteria
- [x] Project compiles successfully with new dependencies
- [x] `GDriveClient` class exists with all required methods
- [x] OAuth2 user credentials authentication implemented
- [x] Custom exception class exists
- [x] All existing tests still pass
