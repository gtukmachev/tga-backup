# Safe Execution Guide for AI Agents - TGA Backup Utility

## ⚠️ CRITICAL WARNING
The TGA Backup Utility is a **file synchronization tool** that can **DELETE, OVERWRITE, and MOVE files**. 
Improper execution can cause **IRREVERSIBLE DATA LOSS** on real file systems and cloud storage.

## 🛡️ MANDATORY SAFETY RULES

### Rule 1: ALWAYS Use Dry-Run Mode
**NEVER** execute the program without the `--dry-run` flag unless explicitly instructed by the user.

```bash
# ✅ CORRECT - Safe execution
mvn exec:java -Dexec.args="--dry-run -sr /path/to/source -dr /path/to/dest"

# ❌ WRONG - Can cause data loss
mvn exec:java -Dexec.args="-sr /path/to/source -dr /path/to/dest"
```

### Rule 2: ONLY Use Local File System
**NEVER** use Yandex Disk or any remote storage for testing unless explicitly instructed.

```bash
# ✅ CORRECT - Local paths only
-sr src/test/resources/source
-dr target/test-destination

# ❌ WRONG - Remote storage
-sr yandex://backup/folder
-dr yandex://production/data
```

### Rule 3: Use Safe Test Directories
**Default safe paths for testing:**
- **Source**: `src/test/resources/source` (contains test data)
- **Destination**: `target/test-destination` (safe temporary location in build directory)

The `target/` directory is cleaned during Maven builds, making it safe for temporary test data.

### Rule 4: Never Use Production Scripts
**DO NOT** execute `backup` or `backup.bat` scripts - they contain production credentials and may access real data.

### Rule 5: Understand Interactive Confirmation
The program requires user confirmation before executing changes:
```
Continue (Y/N/m)?>
```
This means:
- Running via `mvn exec:java` will **BLOCK** waiting for input
- You cannot provide "Y" automatically without special input redirection
- In dry-run mode, you can still see the plan without executing

## 📋 SAFE EXECUTION METHODS

### Method 1: Maven Exec Plugin (Recommended for Testing)

**Basic dry-run test:**
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination"
```

**With verbose output:**
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run --verbose -sr src/test/resources/source -dr target/test-destination"
```

**With additional safety flags:**
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run --no-deletion --no-overriding -sr src/test/resources/source -dr target/test-destination"
```

### Method 2: Build and Run JAR (For Integration Testing)

**Step 1: Build the JAR**
```bash
mvn clean package
```

**Step 2: Run with dry-run**
```bash
java -jar target/tga-backup-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --dry-run -sr src/test/resources/source -dr target/test-destination
```

### Method 3: Using Kotlin Compiler Directly (Advanced)

```bash
kotlinc -cp "$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):target/classes" \
  -script src/main/kotlin/tga/backup/Main.kt -- \
  --dry-run -sr src/test/resources/source -dr target/test-destination
```

## 🔧 PARAMETER REFERENCE

### Safety Parameters (ALWAYS USE THESE)
| Parameter | Short | Description | Safe Value |
|-----------|-------|-------------|------------|
| `--dry-run` | - | Simulates operations without executing | **ALWAYS ON** |
| `--no-deletion` | `-nd` | Prevents file deletion | Recommended |
| `--no-overriding` | `-no` | Prevents file overwriting | Recommended |

### Required Parameters
| Parameter | Short | Description | Example |
|-----------|-------|-------------|---------|
| `--source-root` | `-sr` | Source directory path | `src/test/resources/source` |
| `--destination-root` | `-dr` | Destination directory path | `target/test-destination` |

### Optional Parameters
| Parameter | Short | Description | Default |
|-----------|-------|-------------|---------|
| `--path` | `-p` | Relative path within roots | `*` (all) |
| `--verbose` | - | Show detailed file listings | `false` |
| `--threads` | `-t` | Number of parallel threads | `10` |
| `--exclude` | `-x` | Exclude pattern (can repeat) | See application.conf |

### Dangerous Parameters (AVOID)
| Parameter | Short | Description | Why Dangerous |
|-----------|-------|-------------|---------------|
| `-yu` | - | Yandex username | Accesses cloud storage |
| `-yt` | - | Yandex token | Accesses cloud storage |
| `--remote-cache` | `-rm` | Use remote cache | May access cloud |

## 🧪 TESTING SCENARIOS

### Scenario 1: Basic Dry-Run Test
```bash
# Create test destination
mkdir -p target/test-destination

# Run dry-run
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination"
```

### Scenario 2: Test with Existing Destination Data
```bash
# Prepare destination with some data
mkdir -p target/test-destination
cp -r src/test/resources/source/main/kotlin/tga/functions target/test-destination/

# Run dry-run to see what would change
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination"
```

### Scenario 3: Test Exclusion Patterns
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination -x '*.conf' -x '.md5'"
```

### Scenario 4: Verify Build Without Execution
```bash
# Just compile and run tests
mvn clean test

# Build JAR without running
mvn clean package
```

## 🚫 WHAT NOT TO DO

### ❌ Never Run Without Dry-Run
```bash
# DANGEROUS - Will actually modify files!
mvn exec:java -Dexec.args="-sr src/test/resources/source -dr target/test-destination"
```

### ❌ Never Use Real User Directories
```bash
# DANGEROUS - Could delete personal files!
mvn exec:java -Dexec.args="--dry-run -sr ~/Documents -dr ~/Backup"
```

### ❌ Never Use Cloud Storage
```bash
# DANGEROUS - Could delete cloud data!
mvn exec:java -Dexec.args="--dry-run -sr yandex://folder -dr target/test-destination"
```

### ❌ Never Use Production Scripts
```bash
# DANGEROUS - Contains production credentials!
./backup --dry-run
./backup.bat --dry-run
```

### ❌ Never Modify application.conf Defaults
The default `dryRun = false` in `application.conf` should remain unchanged. Always override via CLI.

## 🔍 VERIFICATION CHECKLIST

Before executing ANY command, verify:

- [ ] `--dry-run` flag is present
- [ ] Source path is `src/test/resources/source` or another safe test directory
- [ ] Destination path is under `target/` directory
- [ ] No Yandex credentials (`-yu`, `-yt`) are used
- [ ] Not using `backup` or `backup.bat` scripts
- [ ] Not using real user directories (~/Documents, ~/Pictures, etc.)
- [ ] Not using cloud storage URLs (yandex://, etc.)

## 📊 UNDERSTANDING DRY-RUN OUTPUT

When running with `--dry-run`, the program will:
1. ✅ Scan source and destination
2. ✅ Compare files and build action plan
3. ✅ Display what WOULD be done:
   - Files to copy
   - Files to override
   - Files to delete
   - Files to move/rename
4. ✅ Show summary statistics
5. ✅ Ask for confirmation (but won't execute even if you say "Y")
6. ✅ Report "dry-run mode" in operation logs

Example output:
```
Creating folders for Copying:....
[DRY RUN] Would create folder: target/test-destination/main/kotlin/tga
.... folders creation finished

Copying files:....
[DRY RUN] Would copy: source/main/kotlin/tga/functions/Main.txt
.... Copying is finished
```

## 🔐 ADDITIONAL SAFETY MEASURES

### 1. Use Version Control
Always commit your work before testing:
```bash
git status
git add .
git commit -m "Before testing backup utility"
```

### 2. Backup Test Data
If modifying test resources:
```bash
cp -r src/test/resources/source src/test/resources/source.backup
```

### 3. Clean Target Directory
Before each test:
```bash
rm -rf target/test-destination
mkdir -p target/test-destination
```

### 4. Monitor File System
Keep an eye on what's happening:
```bash
# In another terminal, watch the destination
watch -n 1 'ls -lR target/test-destination'
```

## 📝 REPORTING ISSUES

If you need to report a bug or test a specific scenario:

1. **Always use dry-run first**
2. **Capture full output**: Add `> output.log 2>&1` to command
3. **Document exact parameters used**
4. **Never include real credentials or paths in reports**

## 🎯 SUMMARY FOR AI AGENTS

**Golden Rule**: When in doubt, add `--dry-run` and use test directories.

**Safe Command Template**:
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination"
```

**Remember**: 
- The program's purpose is to **synchronize files** (copy, override, delete)
- Without `--dry-run`, it **WILL modify the file system**
- The interactive confirmation does NOT prevent execution in non-dry-run mode
- Test directories are your friends: `src/test/resources/source` → `target/test-destination`

---

**Last Updated**: 2026-01-17  
**Version**: 1.0  
**Maintained by**: TGA Backup Project Team
