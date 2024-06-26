/*
 * MIT License
 *
 * Copyright (C) 2020 Frederick Baier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package eu.thesimplecloud.clientserverapi.lib.connection

import eu.thesimplecloud.clientserverapi.lib.handler.packet.IncomingPacketHandler
import eu.thesimplecloud.clientserverapi.lib.packet.IPacket
import eu.thesimplecloud.clientserverapi.lib.packet.PacketData
import eu.thesimplecloud.clientserverapi.lib.packet.WrappedPacket
import eu.thesimplecloud.clientserverapi.lib.packetresponse.WrappedResponseHandler
import eu.thesimplecloud.clientserverapi.lib.packetresponse.responsehandler.ObjectPacketResponseHandler
import eu.thesimplecloud.clientserverapi.lib.promise.CommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.promise.ICommunicationPromise
import java.util.*

abstract class AbstractConnection() : IConnection {

    private val BYTES_PER_FILEPACKET = 50000

    @Volatile
    private var wasCloseIntended: Boolean = false
    @Volatile
    private var sendingFile = false
    private val packetResponseManager by lazy { getCommunicationBootstrap().getPacketResponseManager() }

    private val incomingPacketHandler = IncomingPacketHandler(this)

    @Synchronized
    override fun <T : Any> sendQuery(packet: IPacket, expectedResponseClass: Class<T>, timeout: Long): ICommunicationPromise<T> {
        val packetResponseHandler = ObjectPacketResponseHandler(expectedResponseClass)
        val uniqueId = UUID.randomUUID()
        val packetPromise = CommunicationPromise<T>(timeout)
        packetResponseManager.registerResponseHandler(uniqueId, WrappedResponseHandler(packetResponseHandler, packetPromise))
        this.sendPacket(WrappedPacket(PacketData(uniqueId, packet::class.java.simpleName, false), packet), packetPromise)
        return packetPromise
    }

    fun incomingPacket(wrappedPacket: WrappedPacket) {
        incomingPacketHandler.handleIncomingPacket(wrappedPacket)
    }

    abstract fun sendPacket(wrappedPacket: WrappedPacket, promise: ICommunicationPromise<Any>)

    fun setConnectionCloseIntended() {
        this.wasCloseIntended = true
    }

    override fun wasConnectionCloseIntended(): Boolean {
        return !isOpen() && wasCloseIntended
    }

}