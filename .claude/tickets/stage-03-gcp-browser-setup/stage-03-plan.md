---
currentActivity: "All goals complete: GCP project configured, OAuth2 Desktop credentials created, client_secret.json saved"
nextPlannedActivity: "Transition to Stage 04 — File Transfer"
---

# Stage 03 — Google Cloud Project Setup (Browser via Playwright MCP)

## Goals
Use Playwright MCP browser tools to create OAuth2 credentials for Google Drive API.

## Acceptance criteria
- [x] Google Drive API enabled on GCP project "Snake"
- [x] OAuth consent screen configured (External, testing mode, app name "tga-backup")
- [x] OAuth2 Desktop client "tga-backup-cli" created
- [x] `client_secret.json` downloaded and saved at `~/.tga-backup/client_secret.json`
- [x] File is NOT committed to git (stored outside project directory)
