package redis.connection

import io.ktor.network.sockets.Socket
import redis.protocol.resp2.Resp2
import redis.protocol.resp2.decodeRdb
import redis.response.Response

public class MasterConnection(raw: Socket) : Connection(raw) {
    public val state: ClientConnectionState = ClientConnectionState(address = raw.remoteAddress)

    public val processed: Long
        get() = read.totalBytesRead

    public suspend fun receiveRdb(): ByteArray =
        decodeRdb(read)

    public suspend fun respond(response: Response) {
        response.writeTo(write)
    }
}

public suspend fun MasterConnection.receiveSimpleResponse(): String =
    receive<Resp2.String>().value.also { println("Received simple response $it") }

private suspend fun MasterConnection.send(command: List<String>): String {
    sendCommand(command)
    return receiveSimpleResponse()
}

public suspend fun MasterConnection.ping() {
    val pong = send(listOf("ping"))
    println("received $pong")
}

public suspend fun MasterConnection.replConf(
    listeningPort: Int,
    capabilities: List<String>,
) {
    send(listOf("replconf", "listening-port", listeningPort.toString()))
    val caps = buildList {
        add("replconf")
        for (cap in capabilities) {
            add("capa")
            add(cap)
        }
    }
    val capsResponse = send(caps)

    println("sent replconf: $capsResponse")
}

public suspend fun MasterConnection.startPsync() {
    sendCommand(listOf("psync", "?", "-1"))
    println("Starting psync")
}
