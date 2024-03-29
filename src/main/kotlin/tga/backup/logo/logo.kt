package tga.backup.logo

import io.github.oshai.kotlinlogging.KotlinLogging

val log = KotlinLogging.logger { }

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
            |    $> backup -s <source> -d <destination> <params...>
            |
            |   Parameters:
            |      --dry-run   - to do not perform real file operations
            |      --show-src  - prints content (names) of the source folder
            |      --show-dst  - prints content (names) of the destination folder
            |      --verbose   - activates both --show-src an --show-dst
            |      -y <yandex-token> - optional
        """.trimMargin()
    }

}