package redis.connection

import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import redis.protocol.resp2.Resp2

public class ReplicaConnection(former: Connection) : Connection(former) {

    internal val bytesWritten
        get() = write.totalBytesWritten - handshakeSize

    private var handshakeSize: Long = 0L

    public suspend fun sendRdb(payload: ByteArray) {
        write.writeStringUtf8("\$${payload.size}\r\n")
        write.writeFully(payload)
        handshakeSize = write.totalBytesWritten
    }

    public suspend fun sendSimple(request: String) {
        send(Resp2.String(request))
    }

    public val id: String = "replica#$remoteAddress"

    public companion object
}
