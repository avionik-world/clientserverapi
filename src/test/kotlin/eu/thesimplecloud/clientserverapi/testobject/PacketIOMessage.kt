package eu.thesimplecloud.clientserverapi.testobject

import eu.thesimplecloud.clientserverapi.lib.connection.IConnection
import eu.thesimplecloud.clientserverapi.lib.packet.IPacket
import eu.thesimplecloud.clientserverapi.lib.packet.packettype.ObjectPacket

class PacketIOMessage : ObjectPacket<String>(String::class.java) {

    override suspend fun handle(connection: IConnection): IPacket? = getNewObjectPacketWithContent(false)
}