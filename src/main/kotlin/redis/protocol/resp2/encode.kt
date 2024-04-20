package redis.protocol.resp2

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8

internal suspend fun Resp2.encode(sink: ByteWriteChannel) {
    when (this) {
        is Resp2.Error -> encode(sink)
        is Resp2.List -> encode(sink)
        Resp2.Nil -> Resp2.Nil.encode(sink)
        is Resp2.Number -> encode(sink)
        is Resp2.String -> encode(sink)
    }
}

internal suspend fun Resp2.String.encode(sink: ByteWriteChannel) {
    val length = value.length

    sink.writeStringUtf8("\$$length\r\n$value\r\n")
}

internal suspend fun Resp2.Number.encode(sink: ByteWriteChannel) {
    sink.writeStringUtf8(":$value\r\n")
}

@Suppress("UnusedReceiverParameter")
internal suspend fun Resp2.Nil.encode(sink: ByteWriteChannel) {
    sink.writeStringUtf8("\$-1\r\n")
}

internal suspend fun Resp2.Error.encode(sink: ByteWriteChannel) {
    sink.writeStringUtf8("-$message\r\n")
}

internal suspend fun Resp2.List.encode(sink: ByteWriteChannel) {
    sink.writeStringUtf8("*${this.values.size}\r\n")
    for (value in values) {
        value.encode(sink)
    }
}
