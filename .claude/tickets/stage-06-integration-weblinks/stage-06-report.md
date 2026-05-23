# Stage 06 — Report

## What was done
- Implemented `generateWebLink` in `GDriveFileOps`:
  - Looks up file/folder ID from `pathToIdMap`
  - Returns `https://drive.google.com/drive/folders/{id}` when ID is known
  - Falls back to `https://drive.google.com/drive/` when ID is not in map
- Fixed OAuth 403 blocker: user manually added GTukmachev@gmail.com as a test user
  in GCP Console (APIs & Services → OAuth consent screen → Test users)
- Successfully ran first OAuth flow — token stored at `~/.tga-backup/default/gdrive-token/`
- Dry-run with `gdrive://test-backup` destination completed successfully:
  source scanned (12 folders, 12 files, 32 KB), destination scanned (empty), plan built correctly
- Fixed `~` expansion bug in `-gc` credential path (shell doesn't expand `~` inside quoted args)
- Created `README.md` with Installation section documenting GCP setup steps

## Key insights
- OAuth consent screen in "testing" mode requires explicitly listed test users — the project
  owner does NOT have implicit access (contrary to Stage 03 assumption)
- The `~` character in CLI args passed via `-Dexec.args="..."` is not expanded by the shell;
  the code must handle it explicitly via `replace("~", home)`
