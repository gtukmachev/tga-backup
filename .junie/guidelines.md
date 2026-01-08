### Project Description: TGA Backup Utility

#### Main Goal
The **TGA Backup Utility** is a command-line tool designed to synchronize files and directories between different storage providers. 
Its primary focus is creating and maintaining backups, but not synchronization:
  - It synchronizes always to a single direction: source to destination;
  - Important: It does not modify the source; 
  - It compares destination and source, builds plan of actions (copy, override, delete) to minimize traffic;
  - in plans: add detection of files movements (when it was moved from one to another folder in source), to avoid deletion (in destination) and copying to another destination folder.

#### Algorithm and Principles

The synchronization process follows a strictly defined sequence of phases to ensure efficiency and safety:

1.  **Parameter Parsing & Validation**:
    *   Reads command-line arguments (source, destination, credentials, etc.).
    *   Initializes `FileOps` instances for source and destination based on the URL schemes.
2.  **File Discovery (Scanning)**:
    *   **Source Scanning**: Recursively lists all files in the source. For local files, this includes MD5 calculation and caching (see the MD5 section below).
    *   **Destination Scanning**: Lists files in the destination to establish the current state.
3.  **Comparison & Plan Building**:
    *   Uses `compareSrcAndDst` to identify:
        *   `toAddFiles`: Present in source but missing in destination.
        *   `toOverrideFiles`: Present in both but different (size or MD5).
        *   `toDeleteFiles`: Present in destination but missing in source.
    *   Note: File movement detection (to avoid unnecessary delete/copy) is a planned feature.
4.  **Summary & User Confirmation**:
    *   Displays a detailed table of planned actions (counts and sizes).
    *   Waits for user confirmation before proceeding (unless in dry-run mode).
5.  **Execution Phase**:
    *   **Folder Creation**: Creates all necessary directory structures in the destination first.
    *   **File Transfer**: Executes, using `toAddFiles` and `toOverrideFiles`, via `runCopying()` function.
    *   **runCopying** is working with files in parallel using the multithreading framework `ConsoleMultiThreadWorkers` (see "Multithreading & Console UI" section).
    *   **Cleanup**: Deletes `toDeleteFiles` from the destination.
6.  **Final Reporting**:
    *   Prints a summary of successful operations and any errors encountered.

#### Multithreading & Console UI

The utility uses a custom multithreading framework to handle parallel file operations while maintaining a clean, real-time console interface.

*   **Framework**: `ConsoleMultiThreadWorkers<T>` manages a fixed thread pool (`Executors.newFixedThreadPool`).
*   **Parallelization**:
    *   Mainly used during the **File Transfer** phase.
    *   Each file copy/upload task is submitted to the worker pool.
*   **Status Reporting**:
    *   The framework allocates a specific console line to each worker thread.
    *   It uses ANSI ESC codes (`\u001b[<n>A`) to move the cursor up, update a specific line, and move back down.
    *   **Global Status**: A reserved line (usually at the bottom) displays overall progress (percentage, total size, and current speed).
*   **Real-time Updates**: Workers report their local progress (e.g., "copying file X... 45%") and update the global statistics concurrently.

#### MD5 Calculation & Caching

MD5 checksums are critical for detecting changes in files when sizes are identical. To avoid the high cost of re-calculating hashes on every run, the utility employs a caching mechanism:

*   **Storage**: MD5 hashes for local files are stored in a hidden `.md5` file within each directory.
*   **Cache Format**: A tab-separated file containing: `File Name`, `Size`, `Creation Time`, `Last Modified Time`, and `MD5`.
*   **Validation**: When scanning a file, the utility checks the cache. An entry is considered valid only if the **name**, **size**, and **timestamps** (both creation and last modified) match the current file on disk.
*   **Calculation**: If no valid cache entry is found, the file is read entirely to compute a new MD5, which is then added to the cache.
*   **Persistence**: The `.md5` file is updated and saved only if changes were made to the cache during the scan.

#### Global Statistics

The utility tracks and displays real-time statistics during the transfer phase using the `SyncStatus` and `SpeedCalculator` classes:

*   **Progress Tracking**: Uses `AtomicLong` to safely track the total number of bytes transferred across all threads.
*   **Speed Calculation**:
    *   `SpeedCalculator` maintains a sliding window (default 10 seconds) of progress updates.
    *   It calculates the transfer speed by dividing the bytes transferred within that window by the elapsed time.
*   **Display**: The global status line is formatted as:
    `Global status: [Percentage]% [Loaded Size] / [Total Size] [[Speed]/s]`
*   **Thread Safety**: All statistical updates are thread-safe to ensure accuracy when multiple workers are reporting progress simultaneously.

With built-in support for:
- Local file system.
- Yandex Disk (via REST API).

#### How to Run
The project is built with Maven and can be executed via the `MainKt` class.

Command-line parameters are defined and parsed in `src/main/kotlin/tga/backup/params/Params.kt`. They include:
- Source and Destination paths.
- Dry-run mode.
- Verbosity levels (source/destination listing).
- Yandex Disk credentials.

Example execution:
```bash
java -jar tga-backup.jar -s /path/to/local -d yandex://backup/folder --dry-run
```

#### Code Structure & Segregation Logic
The project follows a clean, modular structure under the `tga.backup` package:
- `tga.backup`: Contains the `Main.kt` entry point and top-level orchestration.
- `tga.backup.files`:
   - `FileOps`: Abstract base class defining the contract for file operations (copy, delete, list).
   - `LocalFileOps` & `YandexFileOps`: Platform-specific implementations.
   - `FileInfo`: Data class for file metadata (name, size, isDirectory, md5).
   - `filesComparator.kt`: Pure functional logic to compare source and destination file sets.
   - `builder.kt`: Factory for creating the appropriate `FileOps` instance based on the URL scheme.
- `tga.backup.params`: Parameter parsing and validation logic.
- `tga.backup.log`: Logging utilities and progress indicators.
- `tga.backup.utils`: Miscellaneous utilities
  - `ConsoleMultiThreadWorkers.kt` - contains a service `ConsoleMultiThreadWorkers` for running tasks in parallel and reporting progress to console in real-time.
    - The service has fixed number of threads (defined as a constructor parameter)
    - Each thread in the executor is consoled with a console line
    - A task (implementation of the `TaskWithStatus` interface) has a callback to report progress (a string) - the service prints the strings to correct lines in console
    - example of running the service: `demo.kt` files (see a `main()` function inside the file).
  

#### Coding Patterns & Best Practices
- **Abstraction over Implementation**: Using the `FileOps` abstract class allows the sync logic to remain platform-agnostic.
- **Data Classes**: Heavy use of Kotlin `data class` for immutable metadata representation (`FileInfo`, `Params`, `SyncActionCases`).
- **Functional Comparison**: The sync logic (`compareSrcAndDst`) is a pure function that operates on sets of `FileInfo`, making it highly testable.
- **Dependency Management**: Uses `junit-bom` in `pom.xml` to ensure version consistency across testing libraries.
- **Error Handling**: 
  - Read errors during scanning are captured in `FileInfo.readException` and reported at the end, rather than failing the entire process.
  - Operation errors during transfer are wrapped in `Result<Unit>` and summarized in the final report.
- **Console Interactivity**: Extensive use of ANSI escape codes for multi-line real-time status updates without scrolling.
- **Platform Specificity**: 
  - `LocalFileOps`: Handles local filesystem IO, MD5 calculation, and cache management.
  - `YandexFileOps`: Manages Yandex Disk communication, pagination, and resumable uploads.

#### Testing Methodology
- **Frameworks**: JUnit 5 for test execution and **AssertJ** for fluent assertions.
- **Assertion Style**: Use `assertThat(...)` from AssertJ. Preferred methods include `.isEmpty()`, `.containsExactly()`, and `.containsExactlyInAnyOrder()`.

#### Useful Notes
- **MD5 Checksums**: Used to detect changes in files even if their size remains identical.
- **Yandex Disk SDK**: Uses `com.yandex.android:disk-restapi-sdk` for cloud interactions.

