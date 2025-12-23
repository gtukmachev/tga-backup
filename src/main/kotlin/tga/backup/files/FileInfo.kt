package tga.backup.files

data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val md5: String? = null
) : Comparable<FileInfo> {

    override fun compareTo(other: FileInfo) = this.name.compareTo(other.name)

}
