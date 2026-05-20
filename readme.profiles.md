# Profiles

## Overview

A profile is a saved set of command-line parameters stored in a file. Instead of typing long argument lists every time, you save them once and invoke by name.

Profiles are stored at `~/.tga-backup/<profile-name>.conf` using [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format (Typesafe Config).

---

## Usage

### Running with a profile

The profile name is the **first positional argument** (before any flags):

```bash
java -jar tga-backup.jar photo --dry-run
```

This loads `~/.tga-backup/photo.conf` and applies `--dry-run` on top. If omitted, the profile name defaults to `default`.

### CLI overrides

CLI arguments always take priority over profile values:

```
CLI args  >  profile config  >  application.conf (defaults)
```

Example — profile has `srcRoot="/data/photos"`, but you override it:
```bash
java -jar tga-backup.jar photo -sr /other/path
```

### Saving/updating a profile

Use `-up` or `--update-profile` to save the current parameters to the profile file:

```bash
java -jar tga-backup.jar photo -sr "/Volumes/SSD/Photo" -dr "yandex://backup/Photo" -yu "user" -yt "token" -up
```

This writes all resolved parameters to `~/.tga-backup/photo.conf`. If the file already exists, the tool shows a diff and asks for confirmation before overwriting.

---

## Config file format

Profile files use HOCON syntax. Example (`~/.tga-backup/photo.conf`):

```hocon
srcRoot="/Volumes/Extreme SSD/Photo"
dstRoot="yandex://PC MSI/Photo"
yandexUser="username"
yandexToken="y0__token..."
noDeletion=true
noOverriding=false
parallelThreads=10
path="*"
dryRun=false
verbose=false
devMode=false
remoteCache=false
exclude=[
    ".md5",
    "._*",
    "Thumbs.db",
    "ZbThumbnail.info",
    "desktop.ini",
    ".tmp.driveupload",
    ".~lock*",
    "picasa.ini",
    ".picasa.ini",
    ".nomedia"
]
```

All fields from the `Params` data class are supported. See `application.conf` for the full list with default values.

---

## Resolution order

```
1. CLI arguments          (highest priority)
2. Profile config file    (~/.tga-backup/<name>.conf)
3. Default config         (application.conf in resources)
```

Each level fills in values not set by the level above.

---

## Directory structure

```
~/.tga-backup/
  default.conf    ← used when no profile name is given
  photo.conf      ← "java -jar tga-backup.jar photo ..."
  video.conf      ← "java -jar tga-backup.jar video ..."
  ...
```

Subdirectories with the same names (e.g. `~/.tga-backup/photo/`) are used for remote cache data when `-rm` is enabled.
