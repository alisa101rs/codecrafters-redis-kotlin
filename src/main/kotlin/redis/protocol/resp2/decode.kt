@file:Suppress("MagicNumber")

package redis.protocol.resp2

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes

public suspend fun Resp2.Companion.decode(source: ByteReadChannel): Resp2 {
    val type = Char(source.readByte().toInt())

    return when (type) {
        '$', '+' -> Resp2.String.decode(source, simple = type == '+')
        '*' -> Resp2.List.decode(source)
        else -> error("Unknown type: $type")
    }
}

private suspend fun Resp2.List.Companion.decode(source: ByteReadChannel): Resp2.List {
    val items = source.readUTF8Line(10)?.toIntOrNull() ?: error("closed")

    val values = (0 until items).map {
        Resp2.decode(source)
    }

    return Resp2.List(values)
}

private suspend fun Resp2.String.Companion.decode(source: ByteReadChannel, simple: Boolean): Resp2.String =
    when (simple) {
        true -> {
            val s = source.readUTF8Line(1024) ?: error("closed")

            Resp2.String(s)
        }

        false -> {
            val length = source.readUTF8Line(18)?.toIntOrNull() ?: error("closed")
            val s = source.readPacket(length + 2).readText(0, length)

            Resp2.String(s)
        }
    }

public suspend fun decodeRdb(source: ByteReadChannel): ByteArray {
    val type = source.readByte()
    check(type == '$'.code.toByte()) { "Not a RDB" }
    val size = source.readUTF8Line(18)?.toIntOrNull() ?: error("closed")

    return source.readPacket(size)
        .readBytes(size)
}
