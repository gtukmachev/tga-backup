### Project Description: TGA Backup Utility

#### Main Goal
The **TGA Backup Utility** is a command-line tool designed to synchronize files and directories between different storage providers. 
Its primary focus is creating and maintaining backups, but not synchronization:
  - It synchronizes always to a single direction: source to destination;
  - Important: It does not modify the source; 
  - It compares destination and source, builds plan of actions (copy, override, delete) to minimize traffic;
  - in plans: add detection of files movements (when it was moved from one to another folder in source), to avoid deletion (in destination) and copying to another destination folder.

Comparing file is by: 
  - full name (as primary key) 
  - size (to identify changes), 
  - MD5 checksums (to identify changes).

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

#### Testing Methodology
- **Frameworks**: JUnit 5 for test execution and **AssertJ** for fluent assertions.
- **Assertion Style**: Use `assertThat(...)` from AssertJ. Preferred methods include `.isEmpty()`, `.containsExactly()`, and `.containsExactlyInAnyOrder()`.

#### Useful Notes
- **MD5 Checksums**: Used to detect changes in files even if their size remains identical.
- **Yandex Disk SDK**: Uses `com.yandex.android:disk-restapi-sdk` for cloud interactions.

