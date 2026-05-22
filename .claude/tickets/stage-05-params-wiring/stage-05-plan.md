---
currentActivity: "All steps complete — params, builder, application.conf updated"
nextPlannedActivity: "Transition to Stage 06"
---

# Stage 05 — Parameters & Wiring

## Goals
Wire up Google Drive as a recognized storage provider: add CLI parameters, update the factory, and update defaults.

## Step-by-step execution plan

1. **Add `gdriveCredentials` field to `Params`**:
   - New field: `gdriveCredentials: String? = null`
   - Add CLI arg mapping: `-gc` / `--gdrive-credentials`
   - Parse it like `yandexUser`/`yandexToken` (nullable string, blank → null)
   - Add to `toString()` output

2. **Update `application.conf`**:
   - Add `gdriveCredentials = ""` default

3. **Update `builder.kt`**:
   - Add `url.startsWith("gdrive")` branch
   - Create `GDriveClient` with credentials path and token store path
   - Create `GDriveFileOps` with the client, profile, cache, exclude patterns
   - Token store path: `~/.tga-backup/<profile>/gdrive-token/`

4. **Verify** compilation and tests

## Acceptance criteria
- `-gc /path/to/client_secret.json` is parsed correctly
- `gdrive://` URLs route to `GDriveFileOps` in `builder.kt`
- Project compiles, all tests pass
