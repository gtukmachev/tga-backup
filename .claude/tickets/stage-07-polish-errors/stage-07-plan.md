---
currentActivity: "All goals complete — Google native file skip, retry with backoff, help text updated"
nextPlannedActivity: "Stage complete"
---

# Stage 07 — Polish & Error Handling

## Goals
Handle Google Drive-specific quirks and edge cases, add retry logic for transient errors, and polish remaining rough edges.

## Step-by-step execution plan

1. **Skip Google Docs/Sheets/Slides during scan**:
   - Google-native files (`application/vnd.google-apps.document`, `.spreadsheet`, `.presentation`, etc.)
     have no size, no MD5, and can't be downloaded via `files.get` — they require export to a specific format
   - These should be excluded from the scan with a log warning, not silently dropped
   - Define `GOOGLE_APPS_MIME_PREFIXES` in `GDriveClient`

2. **Retry logic for transient Google API errors**:
   - Wrap API calls in `GDriveClient` with retry-on-failure for: 403 (rate limit), 429, 500, 503
   - Exponential backoff: 1s, 2s, 4s (max 3 retries)
   - Retry status should be displayed on the thread's console line via `updateStatus` callback
     (e.g., `"Retry 1/3 (waiting 2s)..."`) — no file-based debug logging exists yet, and
     raw println/logger output would corrupt the multi-thread console layout
   - `GDriveClient` methods need an optional status callback parameter for this
   - Google's 20-thread concurrent scan can easily hit rate limits

3. **Add `-gc` to help text** in `logo.kt`

4. **Verify** compilation and all tests pass

## Acceptance criteria
- Google Docs/Sheets files are skipped during scan with a log message
- Transient API errors (403 rate limit, 429, 500, 503) are retried with backoff
- Help text includes `-gc` parameter
- Project compiles, all tests pass
