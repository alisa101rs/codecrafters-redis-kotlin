package redis.engine

import kotlinx.datetime.Instant
import redis.storage.values.StreamId

public interface Engine {
    public suspend fun get(key: String): String?
    public suspend fun set(key: String, value: String, expiration: Instant?)
    public suspend fun type(key: String): String

    public suspend fun add(stream: String, id: StreamId, values: List<String>): StreamId
    public suspend fun range(
        stream: String,
        start: StreamId,
        inclusive: Boolean,
        end: StreamId,
        count: Int,
    ): List<Pair<StreamId, List<String>>>
    public suspend fun wait(): WaitBuilder
}
