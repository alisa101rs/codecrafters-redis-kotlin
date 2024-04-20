package redis.connection

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import redis.protocol.resp2.Resp2
import redis.protocol.resp2.decode
import redis.protocol.resp2.encode
import redis.response.None
import redis.response.Resp2Response
import redis.response.Response
import redis.response.Upgrade
import kotlin.coroutines.CoroutineContext

public abstract class Connection(
    private val raw: Socket,
    protected val read: ByteReadChannel = raw.openReadChannel(),
    protected val write: ByteWriteChannel = raw.openWriteChannel(autoFlush = true),
) {
    protected constructor(other: Connection) : this(other.raw, other.read, other.write)

    public val remoteAddress: SocketAddress
        get() = raw.remoteAddress

    @Suppress("UncheckedCast")
    internal suspend inline fun <reified T : Resp2> receive(): T =
        Resp2.decode(source = read) as T

    internal suspend fun<T : Resp2> send(value: T) {
        value.encode(write)
    }

    public fun commands(context: CoroutineContext): ReceiveChannel<List<String>> = flow {
        try {
            while (true) {
                val command = receiveCommand()

                emit(command)
            }
        } catch (_: ClosedReceiveChannelException) {
            return@flow
        }
    }
        .buffer(0)
        .produceIn(CoroutineScope(context + Dispatchers.Default))

    public suspend fun sendCommand(commands: List<String>) {
        Resp2.List(commands).encode(write)
    }
}

public suspend fun Connection.receiveCommand(): List<String> =
    receive<Resp2.List>()
        .values
        .map {
            val string = it as? Resp2.String ?: error("Expected ro receive list of strings")
            string.value
        }

internal suspend fun Response.writeTo(output: ByteWriteChannel) {
    when (this) {
        Upgrade, None -> {}
        is Resp2Response -> body.encode(sink = output)
    }
}
