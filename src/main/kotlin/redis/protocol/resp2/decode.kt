@file:Suppress("MagicNumber")

package redis.protocol.resp2

import io.ktor.util.moveToByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.bits.set
import io.ktor.utils.io.bits.slice
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.discardExact
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUntilDelimiter
import java.nio.ByteBuffer

public suspend fun Resp2.Companion.decode(source: ByteReadChannel): Resp2 {
    val type = Char(source.readByte().toInt())

    return when (type) {
        '$', '+' -> Resp2.String.decode(source, simple = type == '+')
        '*' -> Resp2.List.decode(source)
        '\n' -> decode(source)
        else -> error("Unknown type: `${type.code}` `${source.readAvailable().joinToString(separator = ",") { it.toString() }}`")
    }
}

private suspend fun ByteReadChannel.readAvailable(): ByteArray {
    var m = ByteArray(0)
    read { buffer ->
        m += buffer.moveToByteArray()
    }
    return m
}

private suspend fun Resp2.List.Companion.decode(source: ByteReadChannel): Resp2.List {
    val items = source.readUTF8Line(10)?.toIntOrNull() ?: error("closed")

    val values = (0 until items).map {
        Resp2.decode(source)
    }

    return Resp2.List(values)
}
private val DELIMETER = ByteBuffer.allocate(2).apply {
    set(0, '\r'.code.toByte())
    set(1, '\n'.code.toByte())
}

private suspend fun Resp2.String.Companion.decode(source: ByteReadChannel, simple: Boolean): Resp2.String =
    when (simple) {
        true -> {
            val output = ByteBuffer.allocate(1024)

            val read = source.readUntilDelimiter(DELIMETER, output)
            if (read == 1024) error("String is too big")

            val s = output.slice(0, read).moveToByteArray().toString(Charsets.UTF_8)
            source.discardExact(2)

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
    val size = source.readUTF8Line(128)
        .also {
            println("Read $it")
        }
        ?.toIntOrNull() ?: error("closed")

    return source.readPacket(size)
        .readBytes(size)
}
