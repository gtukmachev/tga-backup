package tga.backup.utils

fun interface RunnableWithStringStatus {
    fun run(updateStatus: (String) -> Unit)
}
