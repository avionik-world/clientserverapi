package eu.thesimplecloud.clientserverapi.filetransfer.directory

import java.io.File

class DirectorySyncManager : IDirectorySyncManager {

    private val directorySyncList: MutableList<DirectorySync> = ArrayList()

    override fun createDirectorySync(directory: File, toDirectory: String): IDirectorySync {
        val directorySync = DirectorySync(directory, toDirectory)
        directorySyncList.add(directorySync)
        return directorySync
    }

    override fun deleteDirectorySync(directory: File) {
        val directorySync = getDirectorySync(directory)
        if (directorySync !is DirectorySync) return
        this.directorySyncList.remove(directorySync)
    }

    override fun getDirectorySync(directory: File): IDirectorySync? = this.directorySyncList.firstOrNull { it.getDirectory() == directory }
}