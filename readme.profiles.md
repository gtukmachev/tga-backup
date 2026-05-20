# Profiles

## Overview

A profile is a saved set of command-line parameters stored in a file. Instead of typing long argument lists every time, you save them once and invoke by name.

Profiles are stored at `~/.tga-backup/<profile-name>.conf` using [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) format (Typesafe Config).

---

## Usage

### Running with a profile

The profile name is the **first positional argument** (before any flags):

```bash
./backup photo --dry-run
```

This loads `~/.tga-backup/photo.conf` and applies `--dry-run` on top. If omitted, the profile name defaults to `default`.

### CLI overrides

CLI arguments always take priority over profile values:

```
CLI args  >  profile config  >  application.conf (defaults)
```

Example — profile has `srcRoot="/data/photos"`, but you override it:
```bash
./backup photo -sr /other/path
```

### Saving/updating a profile

Use `-up` or `--update-profile` to save the current parameters to the profile file:

```bash
./backup photo -sr "/Volumes/SSD/Photo" -dr "yandex://backup/Photo" -yu "user" -yt "token" -up
```

This writes all resolved parameters to `~/.tga-backup/photo.conf`. If the file already exists, the tool shows a diff and asks for confirmation before overwriting.

---

## Directory structure

```
~/.tga-backup/
  default.conf    ← used when no profile name is given
  photo.conf      ← "./backup photo ..."
  video.conf      ← "./backup video ..."
  ...
```

Subdirectories with the same names (e.g. `~/.tga-backup/photo/`) are used for remote cache data when `-rm` is enabled.
