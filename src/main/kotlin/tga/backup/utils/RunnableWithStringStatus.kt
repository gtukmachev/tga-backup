package tga.backup.utils

fun interface TaskWithStatus<T> {
    fun run(updateStatus: (String) -> Unit): T
}
