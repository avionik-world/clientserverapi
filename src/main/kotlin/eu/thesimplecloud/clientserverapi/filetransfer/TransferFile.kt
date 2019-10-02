package eu.thesimplecloud.clientserverapi.filetransfer

import org.apache.commons.io.FileUtils
import java.io.File
import kotlin.collections.ArrayList

class TransferFile {

    private val byteList = ArrayList<Byte>()

    fun addBytes(byteArray: Array<Byte>) {
        byteList.addAll(byteArray)
    }

    fun buildToFile(file: File) {
        FileUtils.writeByteArrayToFile(file, byteList.toByteArray())
    }

}