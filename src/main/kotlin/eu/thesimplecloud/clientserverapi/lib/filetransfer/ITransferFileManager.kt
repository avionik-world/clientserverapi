package eu.thesimplecloud.clientserverapi.lib.filetransfer

import java.util.*

interface ITransferFileManager {

    /**
     * Adds the specified bytes to the [TransferFile] found by the specified uuid
     */
    fun addBytesToTransferFile(uuid: UUID, byteArray: Array<Byte>)

    /**
     * Writes all bytes from the [TransferFile] found by the specified uuid to the specified file
     */
    fun fileTransferComplete(uuid: UUID, savePath: String)


}