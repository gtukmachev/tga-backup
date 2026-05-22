# Stage 05 — Report

## What was done
- Added `gdriveCredentials` field to `Params` (nullable string, blank → null)
- Added CLI argument mapping: `-gc` / `--gdrive-credentials`
- Updated `Params.toString()` to include gdriveCredentials
- Updated `application.conf` with `gdriveCredentials = ""` default
- Updated `builder.kt` to route `gdrive://` URLs to `GDriveFileOps`:
  - `buildGDriveClient()` creates `GDriveClient` with credentials and token store paths
  - Default credentials: `~/.tga-backup/client_secret.json`
  - Token store: `~/.tga-backup/<profile>/gdrive-token/`

## Key insight
The credentials path defaults to `~/.tga-backup/client_secret.json` (matching where Stage 03
downloaded it), but can be overridden via `-gc`. This keeps the common case simple while
allowing flexibility.
