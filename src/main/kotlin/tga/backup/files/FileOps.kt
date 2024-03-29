package tga.backup.files

interface FileOps {

    fun getFilesSet(rootPath: String): Set<FileInfo>

}