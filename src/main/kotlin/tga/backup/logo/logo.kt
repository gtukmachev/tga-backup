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
            |    $> backup -sr <source-root> -dr <destination-root> [-p <path>] <params...>
            |
            |   Parameters:
            |      -sr, --source-root      - source root folder
            |      -dr, --destination-root - destination root folder
            |      -p,  --path             - relative path to sync (default: *)
            |      -t,  --threads          - number of parallel threads (default: 10)
            |      --dry-run               - to do not perform real file operations
            |      --verbose               - prints content (names) of the source and destination folders
            |      -yu <yandex-user>       - yandex user name
            |      -yt <yandex-token>      - yandex token
        """.trimMargin()
    }

}