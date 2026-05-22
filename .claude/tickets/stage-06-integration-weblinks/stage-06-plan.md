---
currentActivity: "generateWebLink implemented; dry-run test not yet run"
nextPlannedActivity: "Test dry-run with gdrive:// destination, then verify all tests pass"
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

2. **Test dry-run** with `gdrive://` destination:
   - Run: `echo n | mvn exec:java -Dexec.mainClass=tga.backup.MainKt -Dexec.args="-sr src/test/resources/source -dr gdrive://test-backup --dry-run -gc ~/.tga-backup/client_secret.json"`
   - Verify: parameters parse correctly, GDriveFileOps is created

3. **Verify** compilation and all tests pass

## Acceptance criteria
- `generateWebLink` returns correct Google Drive URLs for paths with known IDs
- Dry-run with `gdrive://` destination parses and creates the right FileOps
- Project compiles, all tests pass
