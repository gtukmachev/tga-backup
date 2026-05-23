# Stage 02 — Report

## What was done
- Created `GDriveFileOps` extending `FileOps` with full implementation:
  - `getFilesSet`: concurrent recursive scan with Phaser pattern (same as Yandex)
  - `mkDirs`: nested folder creation with path-to-ID tracking
  - `deleteFileOrFolder`: path-to-ID resolution then delete
  - `moveFileOrFolder`: smart move vs rename detection
  - `close`: delegates to GDriveClient
  - `copyFile`: stubbed for Stage 04
- Maintains `pathToIdMap` (ConcurrentHashMap) to bridge path-based FileOps API with ID-based Google Drive API
- Updated `CopyDirectionIsNotSupportedYet` message to mention gdrive

## Key insight
The path-to-ID mapping is the central challenge. Google Drive is ID-based, but the
backup engine works with paths. `GDriveFileOps` builds this mapping during scan and
maintains it during mkdir/move operations so that subsequent operations can resolve
paths to IDs without extra API calls.
