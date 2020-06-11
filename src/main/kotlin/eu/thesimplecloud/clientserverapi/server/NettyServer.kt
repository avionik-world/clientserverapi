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

package eu.thesimplecloud.clientserverapi.server

import eu.thesimplecloud.clientserverapi.lib.debug.DebugMessage
import eu.thesimplecloud.clientserverapi.lib.debug.DebugMessageManager
import eu.thesimplecloud.clientserverapi.lib.debug.IDebugMessageManager
import eu.thesimplecloud.clientserverapi.lib.defaultpackets.PacketIOConnectionWillClose
import eu.thesimplecloud.clientserverapi.lib.directorywatch.DirectoryWatchManager
import eu.thesimplecloud.clientserverapi.lib.directorywatch.IDirectoryWatchManager
import eu.thesimplecloud.clientserverapi.lib.filetransfer.ITransferFileManager
import eu.thesimplecloud.clientserverapi.lib.filetransfer.TransferFileManager
import eu.thesimplecloud.clientserverapi.lib.filetransfer.directory.DirectorySyncManager
import eu.thesimplecloud.clientserverapi.lib.filetransfer.directory.IDirectorySyncManager
import eu.thesimplecloud.clientserverapi.lib.handler.DefaultConnectionHandler
import eu.thesimplecloud.clientserverapi.lib.handler.DefaultServerHandler
import eu.thesimplecloud.clientserverapi.lib.handler.IConnectionHandler
import eu.thesimplecloud.clientserverapi.lib.handler.IServerHandler
import eu.thesimplecloud.clientserverapi.lib.packet.IPacket
import eu.thesimplecloud.clientserverapi.lib.packet.PacketDecoder
import eu.thesimplecloud.clientserverapi.lib.packet.PacketEncoder
import eu.thesimplecloud.clientserverapi.lib.packet.packettype.BytePacket
import eu.thesimplecloud.clientserverapi.lib.packet.packettype.JsonPacket
import eu.thesimplecloud.clientserverapi.lib.packet.packettype.ObjectPacket
import eu.thesimplecloud.clientserverapi.lib.packetmanager.IPacketManager
import eu.thesimplecloud.clientserverapi.lib.packetmanager.PacketManager
import eu.thesimplecloud.clientserverapi.lib.packetresponse.IPacketResponseManager
import eu.thesimplecloud.clientserverapi.lib.packetresponse.PacketResponseManager
import eu.thesimplecloud.clientserverapi.lib.promise.CommunicationPromise
import eu.thesimplecloud.clientserverapi.lib.promise.ICommunicationPromise
import eu.thesimplecloud.clientserverapi.server.client.clientmanager.ClientManager
import eu.thesimplecloud.clientserverapi.server.client.clientmanager.IClientManager
import eu.thesimplecloud.clientserverapi.server.client.connectedclient.IConnectedClientValue
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.concurrent.DefaultEventExecutorGroup
import io.netty.util.concurrent.EventExecutorGroup
import org.reflections.Reflections


class NettyServer<T : IConnectedClientValue>(
        val host: String,
        val port: Int,
        private val connectionHandler: IConnectionHandler = DefaultConnectionHandler(),
        private val serverHandler: IServerHandler<T> = DefaultServerHandler()
) : INettyServer<T> {

    private val debugMessageManager = DebugMessageManager()
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private var eventExecutorGroup: EventExecutorGroup? = null
    val packetManager = PacketManager()
    val packetResponseManager = PacketResponseManager()
    val clientManager = ClientManager(this)
    private var active = false
    private val transferFileManager = TransferFileManager()
    private val directoryWatchManager = DirectoryWatchManager()
    private val directorySyncManager = DirectorySyncManager(directoryWatchManager)
    private var listening = false
    private var packetClassConverter: (Class<out IPacket>) -> Class<out IPacket> = { it }
    @Volatile
    private var classLoaderToSearchPackets: ClassLoader = this::class.java.classLoader
    @Volatile
    private var classLoaderToSearchObjectPacketClasses: ClassLoader = this::class.java.classLoader

    override fun start(): ICommunicationPromise<Unit> {
        addPacketsByPackage("eu.thesimplecloud.clientserverapi.lib.defaultpackets")
        check(!this.active) { "Can't start server multiple times." }
        this.active = true
        val startPromise = CommunicationPromise<Unit>(enableTimeout = false)
        directoryWatchManager.startThread()
        this.bossGroup = NioEventLoopGroup()
        this.workerGroup = NioEventLoopGroup()
        val bootstrap = ServerBootstrap()
        bootstrap.group(this.bossGroup, this.workerGroup)
        bootstrap.channel(NioServerSocketChannel::class.java)
        val instance = this
        this.eventExecutorGroup = DefaultEventExecutorGroup(20)
        bootstrap.childHandler(object : ChannelInitializer<SocketChannel>() {
            @Throws(Exception::class)
            override fun initChannel(ch: SocketChannel) {
                val pipeline = ch.pipeline()
                pipeline.addLast("frameDecoder", LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                pipeline.addLast(PacketDecoder(instance, packetManager))
                pipeline.addLast("frameEncoder", LengthFieldPrepender(4))
                pipeline.addLast(PacketEncoder(instance))
                //pipeline.addLast("streamer", ChunkedWriteHandler())
                pipeline.addLast(eventExecutorGroup, "serverHandler", ServerHandler(instance, connectionHandler))
                pipeline.addLast(LoggingHandler(LogLevel.DEBUG))

            }
        })
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true)
        val channelFuture = bootstrap.bind(host, port)
        channelFuture.addListener { future ->
            if (future.isSuccess) {
                this.listening = true
                serverHandler.onServerStarted(this)
                startPromise.trySuccess(Unit)
            } else {
                shutdown()
                serverHandler.onServerStartException(this, future.cause())
                startPromise.tryFailure(future.cause())
            }
        }
        return startPromise
    }

    override fun addPacketsByPackage(vararg packages: String) {
        packages.forEach { packageName ->
            val reflections = Reflections(packageName, this.classLoaderToSearchPackets)
            val allClasses = reflections.getSubTypesOf(IPacket::class.java)
                    .union(reflections.getSubTypesOf(JsonPacket::class.java))
                    .union(reflections.getSubTypesOf(ObjectPacket::class.java))
                    .union(reflections.getSubTypesOf(BytePacket::class.java))
                    .filter { it != JsonPacket::class.java && it != BytePacket::class.java && it != ObjectPacket::class.java }
            allClasses.forEach { packetClass ->
                if (this.getDebugMessageManager().isActive(DebugMessage.REGISTER_PACKET)) println("Registered packet: ${packetClass.simpleName}")
                this.packetManager.registerPacket(this.packetClassConverter(packetClass))
            }
        }
    }

    override fun setPacketSearchClassLoader(classLoader: ClassLoader) {
        this.classLoaderToSearchPackets = classLoader
    }

    override fun setClassLoaderToSearchObjectPacketClasses(classLoader: ClassLoader) {
        this.classLoaderToSearchObjectPacketClasses = classLoader
    }

    override fun getClassLoaderToSearchObjectPacketsClasses(): ClassLoader {
        return this.classLoaderToSearchObjectPacketClasses
    }

    override fun getClientManager(): IClientManager<T> = this.clientManager

    override fun getPacketManager(): IPacketManager = this.packetManager

    override fun getTransferFileManager(): ITransferFileManager = this.transferFileManager

    override fun getDirectorySyncManager(): IDirectorySyncManager = this.directorySyncManager

    override fun getDirectoryWatchManager(): IDirectoryWatchManager = this.directoryWatchManager

    override fun getDebugMessageManager(): IDebugMessageManager = this.debugMessageManager

    override fun isActive(): Boolean = this.active

    override fun isListening(): Boolean = this.listening

    override fun shutdown(): ICommunicationPromise<Unit> {
        if (!listening) return CommunicationPromise.of(Unit)
        getClientManager().sendPacketToAllClients(PacketIOConnectionWillClose())
        val shutdownPromise = CommunicationPromise<Unit>()
        this.listening = false
        this.bossGroup?.shutdownGracefully()
        this.workerGroup?.shutdownGracefully()
        this.eventExecutorGroup?.shutdownGracefully()?.addListener {
            if (it.isSuccess) {
                shutdownPromise.trySuccess(Unit)
            } else {
                shutdownPromise.tryFailure(it.cause())
            }
        }
        this.serverHandler.onServerShutdown(this)
        this.active = false
        return shutdownPromise
    }

    override fun setPacketClassConverter(function: (Class<out IPacket>) -> Class<out IPacket>) {
        this.packetClassConverter = function
    }

    override fun getResponseManager(): IPacketResponseManager {
        return this.packetResponseManager
    }

}