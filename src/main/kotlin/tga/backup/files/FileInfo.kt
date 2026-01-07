package tga.backup.files

data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val creationTime: Long = 0L,
    val lastModifiedTime: Long = 0L,
) : Comparable<FileInfo> {

    val md5: String? get() = md5Value

    private var md5Value: String? = null
    var readException: Throwable? = null

    fun setupMd5(md5: String) { md5Value = md5 }

    override fun compareTo(other: FileInfo) = this.name.compareTo(other.name)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileInfo

        if (name != other.name) return false
        if (isDirectory != other.isDirectory) return false
        if (size != other.size) return false
        if (creationTime != other.creationTime) return false
        if (lastModifiedTime != other.lastModifiedTime) return false
        if (md5 != other.md5) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + creationTime.hashCode()
        result = 31 * result + lastModifiedTime.hashCode()
        result = 31 * result + (md5?.hashCode() ?: 0)
        return result
    }

}
