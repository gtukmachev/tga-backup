package tga.backup.files

data class FileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val md5: String? = null
) : Comparable<FileInfo> {

    override fun compareTo(other: FileInfo) = this.name.compareTo(other.name)

    fun sizeReadable(minLength: Int): String {
        return when {
            size > GB -> "${size / GB} g"
            size > MB -> "${size / MB} m"
            size > KB -> "${size / KB} k"
            else -> "$size b"
        }.padStart(minLength)
    }

    companion object {
        private const val GB: Long = 1024 * 1024 * 1024
        private const val MB: Long = 1024 * 1024
        private const val KB: Long = 1024
    }
}
