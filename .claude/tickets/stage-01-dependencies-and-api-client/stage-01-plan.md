---
currentActivity: "stage planning"
nextPlannedActivity: "Add Maven dependencies for Google Drive API"
---

# Stage 01 — Dependencies & Google Drive API Client

## Goals
Create the low-level Google Drive API client that will be used by `GDriveFileOps`.

## Step-by-step execution plan

1. **Add Maven dependencies** to `pom.xml`:
   - `google-api-services-drive` (v3) — Google Drive API
   - `google-api-client` — core Google API client
   - `google-auth-library-oauth2-http` — Service Account auth

2. **Create `tga.backup.gdrive.GDriveClient`** — low-level wrapper:
   - Constructor: takes a path to a Service Account JSON key file + optional Shared Drive ID
   - `authenticate()` — builds a `Drive` service instance using `GoogleCredential`
   - `listFiles(folderId: String, pageToken: String?)` — paginated file listing
   - `getFileMetadata(fileId: String)` — get single file/folder metadata
   - `createFolder(name: String, parentId: String)` — create a folder
   - `uploadFile(localFile: File, parentId: String, mimeType: String, onProgress)` — resumable upload
   - `downloadFile(fileId: String, outputStream: OutputStream, onProgress)` — download
   - `deleteFile(fileId: String)` — trash or permanently delete
   - `moveFile(fileId: String, newParentId: String, newName: String?)` — move/rename
   - `close()` — cleanup

3. **Create `tga.backup.gdrive.GDriveResponseException`** — custom exception (mirrors `YandexResponseException`)

4. **Verify** the project compiles with `mvn compile`

## Acceptance criteria
- Project compiles successfully with new dependencies
- `GDriveClient` class exists with all listed methods
- Authentication via Service Account JSON key file is implemented
- Custom exception class exists
