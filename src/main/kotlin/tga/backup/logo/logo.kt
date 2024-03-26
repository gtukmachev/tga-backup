package tga.backup.logo

fun printLogo() {
    println(
        """
        |   +--------- Backup utility --------------------------------------------+
        |   |  Syncs one folder to another using minimum file-system operations.  |
        |   |  Skips the same files.                                              |
        |   |  Supports local file-system and clouds: Yandex Disk, Google drive   |
        |   |                                                                     |
        |   |  how to run:                                                        |
        |   |                                                                     |
        |   |  $> backup -s <source> -d <destination> [--dry-run]                 |
        |   |                                                                     |
        |   +---------------------------------------------------------------------+
        """.trimMargin()
    )

}