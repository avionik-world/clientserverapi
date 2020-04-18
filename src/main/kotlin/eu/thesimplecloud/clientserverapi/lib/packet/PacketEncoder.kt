package eu.thesimplecloud.clientserverapi.lib.packet

import eu.thesimplecloud.clientserverapi.lib.ByteBufStringHelper
import eu.thesimplecloud.clientserverapi.lib.bootstrap.ICommunicationBootstrap
import eu.thesimplecloud.clientserverapi.lib.debug.DebugMessage
import eu.thesimplecloud.clientserverapi.lib.json.JsonData
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class PacketEncoder(private val communicationBootstrap: ICommunicationBootstrap) : MessageToByteEncoder<WrappedPacket>() {

    @Synchronized
    override fun encode(ctx: ChannelHandlerContext, wrappedPacket: WrappedPacket, byteBuf: ByteBuf) {
        val jsonData = JsonData()
        val packetData = PacketData(wrappedPacket.packetData.uniqueId, wrappedPacket.packetData.id, wrappedPacket.packet::class.java.simpleName)
        jsonData.append("data", packetData)
        ByteBufStringHelper.writeString(byteBuf, jsonData.getAsJsonString())
        wrappedPacket.packet.write(byteBuf, communicationBootstrap)
        if (this.communicationBootstrap.getDebugMessageManager().isActive(DebugMessage.PACKET_SENT))
            println("Sent packet ${wrappedPacket.packet::class.java.simpleName}")
    }
}