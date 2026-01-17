package tga.backup.logo

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger { }

fun printLogo() {
    log.info {
        """
            |
            |Backup utility
            |    Syncs one folder to another using minimum file-system operations.
            |    Skips the same files.
            |    Supports local file-system and clouds: Yandex Disk, Google drive
            |
            |    how to run:
            |
            |    $> backup [profile] -sr <source-root> -dr <destination-root> [-p <path>] <params...>
            |
            |   Parameters:
            |      -sr, --source-root      - source root folder
            |      -dr, --destination-root - destination root folder
            |      -p,  --path             - relative path to sync (default: *)
            |      -t,  --threads          - number of parallel threads (default: 10)
            |      -nd, --no-deletion      - skip deletion phase (will be planned but not executed)
            |      -no, --no-overriding    - skip overriding phase (will be planned but not executed)
            |      -x,  --exclude          - exclude files matching pattern (can be used multiple times)
            |      -rm, --remote-cache     - use remote cache for file listings
            |      --dry-run               - to do not perform real file operations
            |      --verbose               - prints content (names) of the source and destination folders
            |      -dev                    - development mode
            |      -yu <yandex-user>       - yandex user name
            |      -yt <yandex-token>      - yandex token
            |      -up, --update-profile   - update profile configuration file
        """.trimMargin()
    }

}