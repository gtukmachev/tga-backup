---
currentActivity: "stage planning"
nextPlannedActivity: "Open Google Cloud Console in browser via Playwright MCP"
---

# Stage 03 — Google Cloud Project Setup (Browser via Playwright MCP)

## Goals
Use Playwright MCP browser tools to create a GCP project, enable Drive API,
and generate OAuth2 Desktop credentials (`client_secret.json`).

## Step-by-step execution plan

1. **Navigate** to Google Cloud Console (console.cloud.google.com)
2. **User signs in** manually (agent waits)
3. **Create or select** a GCP project
4. **Enable** the Google Drive API (APIs & Services > Library)
5. **Configure OAuth consent screen**:
   - User type: External
   - App name: "tga-backup"
   - Scopes: Google Drive API
   - Test users: add user's email
6. **Create credentials**:
   - Credentials > Create Credentials > OAuth client ID
   - Application type: Desktop app
   - Name: "tga-backup-cli"
7. **Download** `client_secret.json`
8. **Place** the file in a safe location (e.g. `~/.tga-backup/client_secret.json`)
9. **Add** `client_secret.json` to `.gitignore` if placed in project directory

## Acceptance criteria
- `client_secret.json` exists with valid OAuth2 client credentials
- File is NOT committed to git (contains secrets)
