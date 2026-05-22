@.junie/guidelines.md
@.junie/safe-execution-guide.md
@.junie/scripts.md

## Staged Development

This project uses staged development. See the plan and current stage:
- **Main plan:** `.claude/tickets/main-plan.md`
- **Current stage:** @.claude/tickets/stage-06-integration-weblinks/stage-06-plan.md

### Current activity (Stage 06)
- Stages 01-05 are **complete**: GDrive API client, GDriveFileOps, GCP credentials, file transfer (upload/download with progress), CLI params & URL routing.
- Stage 06 in progress: `generateWebLink` is implemented in `GDriveFileOps`. Next step is testing dry-run with `gdrive://` destination and verifying all tests pass.
- Key fix applied in Stage 04: `pathToIdMap` now stores full paths (e.g., `backup/photos`) instead of relative-to-root paths, so `mkDirs`/`copyFile`/`delete`/`move` resolve IDs correctly.
