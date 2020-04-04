package eu.thesimplecloud.clientserverapi.lib.filetransfer.directory

import eu.thesimplecloud.clientserverapi.lib.defaultpackets.PacketIOCreateDirectory
import eu.thesimplecloud.clientserverapi.lib.connection.IConnection
import eu.thesimplecloud.clientserverapi.lib.directorywatch.IDirectoryWatch
import eu.thesimplecloud.clientserverapi.lib.directorywatch.IDirectoryWatchListener
import eu.thesimplecloud.clientserverapi.lib.defaultpackets.PacketIODeleteFile
import eu.thesimplecloud.clientserverapi.lib.defaultpackets.PacketIOUnzipZipFile
import eu.thesimplecloud.clientserverapi.lib.promise.CommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.promise.ICommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.promise.combineAllPromises
import eu.thesimplecloud.clientserverapi.lib.promise.flatten
import eu.thesimplecloud.clientserverapi.lib.util.ZipUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class DirectorySync(private val directory: File, private val toDirectory: String, private val tmpZipDir: File, directoryWatch: IDirectoryWatch) : IDirectorySync {

    private val receivers = CopyOnWriteArrayList<IConnection>()
    private val zipFile = File(tmpZipDir, directory.name + ".zip")


    init {
        tmpZipDir.mkdirs()
        if (zipFile.exists())
            zipFile.delete()
    }


    init {
        require(this.directory.isDirectory) { "Specified file must be a directory." }

        directoryWatch.addWatchListener(object : IDirectoryWatchListener {
            override fun fileCreated(file: File) {
                println("file created: " + file.path)
                //normal files will be sent in [fileModified]
                GlobalScope.launch {
                    var count = 0
                    while (true) {
                        count++
                        try {
                            changesDetected()
                            receivers.forEach { connection -> sendFileOrDirectory(file, connection) }
                        } catch (e: Exception) {
                            delay(100)
                            if (count == 10 * 50 * 2) {
                                throw IOException(e)
                            }
                            continue
                            //ignore "file is in use by another process"
                        }
                        break
                    }
                }
            }

            override fun fileModified(file: File) {
                println("file modified: " + file.path)
                if (file.isDirectory) return
                try {
                    changesDetected()
                    receivers.forEach { connection -> sendFileOrDirectory(file, connection) }
                } catch (e: Exception) {
                    throw IOException(e)
                }
            }

            override fun fileDeleted(file: File) {
                println("file deleted: " + file.path)
                changesDetected()
                val otherSidePath = getFilePathOnOtherSide(file)
                receivers.forEach { connection -> connection.sendUnitQuery(PacketIODeleteFile(otherSidePath)) }
            }

        })
    }

    private fun changesDetected() {
        synchronized(this) {
            this.zipFile.delete()
        }
    }

    override fun getDirectory(): File = this.directory

    override fun syncDirectory(connection: IConnection): ICommunicationPromise<Unit> {
        synchronized(this) {
            return CommunicationPromise.runAsync {
                if (!zipFile.exists())
                    zipDirectory()
                this.receivers.add(connection)
                val promise = connection.sendFile(zipFile, tmpZipDir.path + "/C-" + zipFile.name, TimeUnit.MINUTES.toMillis(1))
                val sizeInMB = (zipFile.length() / 1000) / 1000
                promise.then { connection.sendUnitQuery(PacketIOUnzipZipFile(tmpZipDir.path + "/C-" + zipFile.name, toDirectory), (sizeInMB * 100) * 2) }.flatten()
            }.flatten()
        }
    }

    private fun sendFileOrDirectory(file: File, connection: IConnection): ICommunicationPromise<Unit> {
        return if (file.isDirectory) {
            val createDirPromise = connection.sendUnitQuery(PacketIOCreateDirectory(getFilePathOnOtherSide(file)))
            createDirPromise.then {
                val files = file.listFiles() ?: emptyArray<File>()
                val promises = files.map { sendFileOrDirectory(it, connection) }
                promises.combineAllPromises()
            }.flatten()
        } else {
            val timeToWait = file.length() + 400
            connection.sendFile(file, getFilePathOnOtherSide(file), timeToWait.toLong())
        }
    }

    override fun syncNoLonger(connection: IConnection) {
        this.receivers.remove(connection)
    }

    private fun getAllFiles(): Collection<File> {
        val acceptAllFilter = object : IOFileFilter {
            override fun accept(file: File?): Boolean {
                return true
            }

            override fun accept(dir: File?, name: String?): Boolean {
                return true
            }
        }
        return FileUtils.listFilesAndDirs(directory, acceptAllFilter, acceptAllFilter)
    }

    private fun getFilePathOnOtherSide(file: File) = toDirectory + file.path.replace(this.directory.path, "")

    private fun zipDirectory() {
        synchronized(this) {
            ZipUtils.zipFiles(zipFile, getAllFiles().filter { !it.isDirectory }, subtractRelativePath = directory.path)
        }
    }

}