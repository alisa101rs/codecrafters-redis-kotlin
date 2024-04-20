package redis.commands

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import redis.engine.RedisEngine
import redis.protocol.resp2.Resp2
import redis.response.NullResponse
import redis.response.Resp2Response
import redis.response.Response
import redis.response.SimpleResponse
import redis.routing.Command
import redis.routing.options.arg
import redis.routing.options.default
import redis.routing.options.flag
import redis.routing.options.resource
import redis.routing.options.transform
import redis.routing.options.vararg
import redis.storage.values.StreamId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public class Xadd : Command() {
    private val engine by resource<RedisEngine>()
    private val streamKey by arg(1)
    private val id by arg(2) { it.toStreamId() }
    private val values by vararg(startPosition = 3)

    override suspend fun handle(): Response {
        val key = engine.add(streamKey, id, values)
        return SimpleResponse(key.toString())
    }
}

public class Xrange : Command() {
    private val engine by resource<RedisEngine>()
    private val streamKey by arg(1)
    private val start by arg(2) { it.toStreamRangeStart() }
    private val count by flag("count") { it.toInt() }.default(Int.MAX_VALUE)
    private val end by arg(3) { it.toStreamRangeEnd() }

    override suspend fun handle(): Response {
        val entries = engine.range(streamKey, start, true, end, count)
            .toResp2()

        return Resp2Response(entries)
    }
}

public class Xread : Command() {
    private val engine by resource<RedisEngine>()
    private val block by flag("block") { it.toLong().takeUnless { n -> n == 0L }?.milliseconds ?: Duration.INFINITE }
    private val count by flag("count") { it.toInt() }.default(Int.MAX_VALUE)
    private val streams by flag("streams", values = Int.MAX_VALUE).transform {
        check(it.size % 2 == 0)
        val half = it.size / 2
        (0 until half)
            .map { index ->
                it[index] to it[half + index].toStreamReadStart()
            }
    }

    override suspend fun handle(): Response {
        val output = mutableListOf<Resp2.List>()

        for ((streamKey, rangeStart) in streams) {
            if (rangeStart == StreamId.MAX) continue
            val range = engine.range(streamKey, rangeStart, false, StreamId.MAX, count)
            if (range.isEmpty()) continue
            output.add(Resp2.List(Resp2.String(streamKey), range.toResp2()))
        }

        if (output.isEmpty() && block != null) {
            try {
                withTimeout(checkNotNull(block)) {
                    engine.wait().forKeys(keys = streams.map { it.first }.toTypedArray())
                }
            } catch (_: TimeoutCancellationException) {
                return NullResponse
            }

            for ((streamKey, rangeStart) in streams) {
                val range = engine.range(streamKey, rangeStart, rangeStart == StreamId.MAX, StreamId.MAX, count)
                if (range.isEmpty()) continue
                output.add(Resp2.List(Resp2.String(streamKey), range.toResp2()))
            }
        }

        return Resp2Response(Resp2.List(output))
    }
}

private fun List<Pair<StreamId, List<String>>>.toResp2(): Resp2.List =
    map { (key, entries) ->
        Resp2.List(
            listOf(
                Resp2.String(key.toString()),
                Resp2.List(entries),
            ),
        )
    }.let(Resp2::List)

private fun String.toStreamReadStart(): StreamId {
    if (this == "$") return StreamId.MAX
    val (ts, c) = split("-")
    return StreamId(ts.toLong(), c.toLong())
}

private fun String.toStreamRangeStart(): StreamId {
    if (this == "-") return StreamId.MIN
    val (ts, c) = split("-")
    return StreamId(ts.toLong(), c.toLong())
}

private fun String.toStreamRangeEnd(): StreamId {
    if (this == "+") return StreamId.MAX
    val (ts, c) = split("-")
    return StreamId(ts.toLong(), c.toLong())
}

private fun String.toStreamId(): StreamId {
    if (this == "*") return StreamId.MAX
    val (ts, c) = split("-")
    return if (c == "*") {
        StreamId(ts.toLong(), Long.MAX_VALUE)
    } else {
        StreamId(ts.toLong(), c.toLong())
    }
}
