# Scripting System

## Overview

The TGA Backup Utility includes a **scripting system** that allows running specialized, task-specific operations through the same entry point as the main backup tool. Scripts are invoked via the `-m` (or `--mode`) command-line parameter and are resolved dynamically at runtime using reflection.

This design lets you add new scripts without modifying `Main.kt` — just create a class following the naming convention and it's automatically available.

---

## How It Works

### Invocation

```bash
java -jar tga-backup.jar -m <mode-name> [other params...]
```

Or with Maven:
```bash
mvn exec:java -Dexec.mainClass=tga.backup.MainKt -Dexec.args="-m <mode-name> ..."
```

### Name Resolution

The `mode` value is converted to a class name using this algorithm:

1. Split by `-` or `_`
2. Capitalize each segment
3. Join and append `Script`
4. Look up `tga.backup.scripts.<Result>`

| `-m` value             | Resolved class                                       |
|------------------------|------------------------------------------------------|
| `backup` (default)     | `tga.backup.scripts.BackupScript`                    |
| `sync`                 | `tga.backup.scripts.BackupScript` *(special alias)*  |
| `duplicates`           | `tga.backup.scripts.DuplicatesScript`                |
| `cleanup`              | `tga.backup.scripts.CleanupScript`                   |
| `del-old-duplicates`   | `tga.backup.scripts.DelOldDuplicatesScript`          |
| `my-new-task`          | `tga.backup.scripts.MyNewTaskScript`                 |

### Execution Flow

```
main(args)
  ├── printLogo()
  ├── readParams(args)          ← parses CLI args, profiles, config
  ├── instantiateScript(params) ← reflection-based class loading
  └── script.run()              ← executes the script
```

---

## Existing Scripts

1. `BackupScript` — Sync source to destination (copy, override, delete, move, rename)
2. `DuplicatesScript` — Detect and report duplicate files/folders by MD5
3. `CleanupScript` — Remove excluded files and empty folders
4. `DelOldDuplicatesScript` — Delete source files that already exist in destination

See KDoc on each class for detailed usage, required params, and examples.

---

## Common Parameters Reference

| Flag                   | Parameter        | Description                                    |
|------------------------|------------------|------------------------------------------------|
| `-m`, `--mode`         | `mode`           | Script to run (default: `backup`)              |
| `-sr`, `--src-root`    | `srcRoot`        | Source root path                                |
| `-dr`, `--dst-root`    | `dstRoot`        | Destination root path                          |
| `-p`, `--path`         | `path`           | Sub-path within roots                          |
| `-ta`, `--target`      | `target`         | Target tree: `src` or `dst`                    |
| `--dry-run`            | `dryRun`         | Preview actions without executing              |
| `-v`, `--verbose`      | `verbose`        | Verbose output                                 |
| `-nd`, `--no-deletion` | `noDeletion`     | Skip file deletions                            |
| `-no`, `--no-override` | `noOverriding`   | Skip file overrides                            |
| `-pt`                  | `parallelThreads`| Number of parallel threads (default: 10)       |
| `-yu`                  | `yandexUser`     | Yandex Disk username                           |
| `-yt`                  | `yandexToken`    | Yandex Disk OAuth token                        |
| `-ex`, `--exclude`     | `exclude`        | Exclusion patterns (repeatable)                |
| `-rm`                  | `remoteCache`    | Enable remote filesystem caching               |
| `-up`, `--update-profile` | —             | Save current params to profile config          |

**Profiles:** The first positional argument (before flags) is treated as a profile name (default: `default`). Profile configs are stored at `~/.tga-backup/<profile>.conf`.

---

## Creating a New Script

### Step-by-step

1. **Create the class** in `src/main/kotlin/tga/backup/scripts/`:

```kotlin
@Suppress("unused") // instantiated via reflection
class MyNewTaskScript(params: Params) : Script(params) {
    override fun run() {
        // Your logic here
    }
}
```

2. **Run it:**
```bash
java -jar tga-backup.jar -m my-new-task [params...]
```

That's it. No registration needed — the reflection-based resolver handles discovery.

### Contract

- **Extend** `Script` (abstract class in `tga.backup.scripts`)
- **Constructor** must accept a single `Params` argument
- **Implement** `fun run()` — the entry point for your script
- **Annotate** with `@Suppress("unused")` to avoid IDE warnings (reflection-only usage)

### Naming Convention

| Class name              | Mode value (`-m`)       |
|-------------------------|-------------------------|
| `MyFeatureScript`       | `my-feature`            |
| `FixDatesScript`        | `fix-dates`             |
| `AnalyzeTreeScript`     | `analyze-tree`          |
| `SomeTaskScript`        | `some-task` or `some_task` |

### Reusable Infrastructure

Scripts have access to the full project infrastructure:

- **`loadTree(name, folder, params)`** — scans a file tree, returns `(FileOps, Set<FileInfo>)` with MD5 caching and exclusions
- **`ExclusionMatcher`** — match files against exclusion patterns
- **`compareSrcAndDst()`** — pure-functional file set comparison
- **`logPhase()` / `formatFileSize()` / `formatNumber()`** — logging and formatting utilities
- **`ConsoleMultiThreadWorkers`** — parallel task execution with console progress display
- **`FileOps`** — abstract file operations (local + Yandex Disk implementations)

### Typical Patterns

**Loading a single tree:**
```kotlin
val (fileOps, files) = loadTree("Source", params.srcFolder, params)
try {
    // process files
} finally {
    fileOps.close()
}
```

**Target-based script (src or dst):**
```kotlin
val targetRoot = when (params.target) {
    "src" -> params.srcRoot
    "dst" -> params.dstRoot
    else -> throw IllegalArgumentException("Invalid target: ${params.target}")
}
```

**User confirmation before destructive action:**
```kotlin
if (params.dryRun) {
    println("Dry-run mode: no changes will be applied.")
    return
}
print("Proceed? (y/n): ")
if (Scanner(System.`in`).next().lowercase() != "y") return
```
