package redis.commands.replica

import kotlinx.datetime.Instant
import redis.engine.RedisEngine
import redis.replication.ReplicationState
import redis.response.ArrayResponse
import redis.response.None
import redis.response.Response
import redis.routing.Command
import redis.routing.options.arg
import redis.routing.options.flag
import redis.routing.options.resource
import redis.routing.options.vararg
import redis.storage.values.StreamId

public class Set : Command() {
    private val engine by resource<RedisEngine>()
    private val key by arg(1)
    private val value by arg(2)
    private val expiresAt by flag("PXAT") { Instant.fromEpochMilliseconds(it.toLong()) }

    override suspend fun handle(): Response {
        engine.set(key, value, expiresAt)
        return None
    }
}

public class Ping : Command() {
    override suspend fun handle(): Response = None
}

public class Ack : Command() {
    private val state by resource<ReplicationState>()

    override suspend fun handle(): Response =
        ArrayResponse(listOf("REPLCONF", "ACK", "${state.replicationOffset}"))
}

public class Xadd : Command() {
    private val engine by resource<RedisEngine>()
    private val streamKey by arg(1)
    private val id by arg(2) { it.toStreamId() }
    private val values by vararg(startPosition = 3)

    override suspend fun handle(): Response {
        engine.add(streamKey, id, values)
        return None
    }
}

private fun String.toStreamId(): StreamId {
    val (ts, c) = split("-")
    return StreamId(ts.toLong(), c.toLong())
}
