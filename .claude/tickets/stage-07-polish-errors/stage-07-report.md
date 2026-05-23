# Stage 07 — Report

## What was done

1. **Skip Google native files during scan**:
   - Added `isGoogleNativeFile()` to `GDriveClient` — detects `application/vnd.google-apps.*`
     MIME types (docs, sheets, slides, forms, etc.) that can't be downloaded via `files.get`
   - Scan logs a warning for each skipped file via `logger.warn`
   - Folders (`vnd.google-apps.folder`) are NOT skipped

2. **Retry logic with exponential backoff**:
   - Added `withRetry()` helper in `GDriveClient` — retries on HTTP 403 (rate limit), 429, 500, 503
   - Exponential backoff: 1s, 2s, 4s (max 3 retries)
   - All API methods accept an optional `onRetry: ((String) -> Unit)?` callback
   - Callers can pass their thread's `updateStatus` function to display retry state on the
     correct console line without corrupting the multi-thread terminal layout
   - Non-retryable errors are thrown immediately

3. **Help text updated**: Added `-gc, --gdrive-credentials` to `logo.kt`

## Key design decisions
- Retry callback is optional (`null` by default) — callers that don't have a console context
  (e.g., `mkDirs` during folder creation phase) simply don't pass it, and retries happen silently
- Google native files are skipped at scan time rather than at download time — this prevents
  them from appearing in the sync plan at all
