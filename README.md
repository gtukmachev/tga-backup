# TGA Backup

A command-line utility for synchronizing files and directories between different storage providers.

Supported providers:
- Local file system
- Yandex Disk (`yandex://`)
- Google Drive (`gdrive://`)

## Installation

### Google Drive

1. Create a GCP project (or use an existing one) at [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the **Google Drive API**: APIs & Services → Library → search "Google Drive API" → Enable
3. Configure the **OAuth consent screen**: APIs & Services → OAuth consent screen
   - User type: External
   - App name: `tga-backup`
   - Add your email as a test user (required while the app is in "Testing" mode):
     APIs & Services → OAuth consent screen → Test users → **+ Add users** → enter your email
   - Direct link (replace `YOUR_PROJECT_ID`):
     `https://console.cloud.google.com/apis/credentials/consent?project=YOUR_PROJECT_ID`
4. Create **OAuth2 Desktop credentials**: APIs & Services → Credentials → Create Credentials → OAuth client ID
   - Application type: Desktop app
   - Download `client_secret.json`
5. Place `client_secret.json` at `~/.tga-backup/client_secret.json` (default location), or pass it via `-gc /path/to/client_secret.json`
6. On first run, a browser window will open for Google login. The refresh token is stored locally at `~/.tga-backup/<profile>/gdrive-token/`

## Usage

```bash
# Local to Google Drive
java -jar tga-backup.jar -sr /path/to/local -dr gdrive://backup/folder

# Google Drive to Local
java -jar tga-backup.jar -sr gdrive://backup/folder -dr /path/to/local

# Local to Yandex Disk
java -jar tga-backup.jar -sr /path/to/local -dr yandex://backup/folder

# Dry run (preview without changes)
java -jar tga-backup.jar -sr /path/to/local -dr gdrive://backup/folder --dry-run
```
