---
currentActivity: "generateWebLink implemented; first auth attempt hit 403 access_denied"
nextPlannedActivity: "Add GTukmachev@gmail.com as test user in GCP OAuth consent screen, then retry auth flow"
---

# Stage 06 — Integration Testing & Web Links

## Goals
Implement web link generation for Google Drive paths and verify end-to-end wiring with dry-run.

## Step-by-step execution plan

1. **Implement `generateWebLink`** for Google Drive paths:
   - Look up file/folder ID from `pathToIdMap`
   - Generate `https://drive.google.com/drive/folders/{id}` for folders
   - Generate `https://drive.google.com/file/d/{id}/view` for files
   - Fall back to path-based search URL if ID not in map

2. **Fix OAuth consent screen** — add GTukmachev@gmail.com as a test user:
   - The OAuth consent screen is in "testing" mode (External), which restricts access to explicitly listed test users
   - Go to GCP Console → APIs & Services → OAuth consent screen → Test users → Add GTukmachev@gmail.com
   - Stage 03 attempted this but the UI didn't persist it; must be done manually or retried via Playwright MCP

3. **Test dry-run** with `gdrive://` destination:
   - Run: `echo n | mvn exec:java -Dexec.mainClass=tga.backup.MainKt -Dexec.args="-sr src/test/resources/source -dr gdrive://test-backup --dry-run -gc ~/.tga-backup/client_secret.json"`
   - Verify: parameters parse correctly, GDriveFileOps is created, OAuth flow succeeds

4. **Verify** compilation and all tests pass

## Acceptance criteria
- `generateWebLink` returns correct Google Drive URLs for paths with known IDs
- Dry-run with `gdrive://` destination parses and creates the right FileOps
- Project compiles, all tests pass
