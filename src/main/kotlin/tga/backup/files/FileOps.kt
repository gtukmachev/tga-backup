package tga.backup.files

import java.io.File

class FileOps {


    fun getFilesSet(root: String): Set<String> {
        if (!File(root).exists()) return emptySet()
        val outSet = HashSet<String>()
        return File(root).listFilesRecursive(outSet)
    }

    private fun File.listFilesRecursive(outSet: MutableSet<String>): Set<String> {
        val content = this.listFiles()!!
        content.forEach { outSet.add(it.path) }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet) }
        return outSet
    }

}

