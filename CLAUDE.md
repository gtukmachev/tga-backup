@.junie/guidelines.md
@.junie/safe-execution-guide.md
@.junie/scripts.md

## Staged Development

This project uses staged development. See the plan and current stage:
- **Main plan:** `.claude/tickets/main-plan.md`
- **Current stage:** @.claude/tickets/stage-06-integration-weblinks/stage-06-plan.md

### Current activity (Stage 06)
- Stages 01-05 are **complete**: GDrive API client, GDriveFileOps, GCP credentials, file transfer (upload/download with progress), CLI params & URL routing.
- Stage 06 in progress: `generateWebLink` is implemented in `GDriveFileOps`.
- **Blocker**: First OAuth auth attempt returned **403 access_denied** — "tga-backup has not completed the Google verification process". The OAuth consent screen is in External/testing mode, and GTukmachev@gmail.com is not listed as a test user. Stage 03 attempted to add the test user via Playwright MCP but it didn't persist.
- **Next step**: Add GTukmachev@gmail.com as a test user in GCP Console (APIs & Services → OAuth consent screen → Test users), then retry the auth flow.
- Key fix applied in Stage 04: `pathToIdMap` now stores full paths (e.g., `backup/photos`) instead of relative-to-root paths, so `mkDirs`/`copyFile`/`delete`/`move` resolve IDs correctly.
