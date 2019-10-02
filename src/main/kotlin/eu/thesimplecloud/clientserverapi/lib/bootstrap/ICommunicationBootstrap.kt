package eu.thesimplecloud.clientserverapi.lib.bootstrap

import eu.thesimplecloud.clientserverapi.filetransfer.ITransferFileManager
import eu.thesimplecloud.clientserverapi.filetransfer.directory.IDirectorySyncManager

interface ICommunicationBootstrap : IBootstrap {

    /**
     * Returns the [ITransferFileManager] to transfer files
     */
    fun getTransferFileManager(): ITransferFileManager

    /**
     * Returns the [IDirectorySyncManager] to synchronize directories between server and client
     */
    fun getDirectorySyncManager(): IDirectorySyncManager

}