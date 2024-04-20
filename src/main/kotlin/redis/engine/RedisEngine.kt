package redis.engine

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import redis.storage.Storage
import redis.storage.values.RedisValue
import redis.storage.values.Stream
import redis.storage.values.StreamId

public class RedisEngine(private val storage: Storage) : Engine {
    private val replication: Channel<UpdateEvent> = Channel(capacity = 256)
    private val updates: MutableSharedFlow<UpdateEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val lock: Mutex = Mutex()

    public val replicationChannel: ReceiveChannel<UpdateEvent>
        get() = replication

    override suspend fun get(key: String): String? {
        lock.withLock {
            val value = storage.get(key) ?: return null

            return when (value) {
                is RedisValue.String -> value.value
                else -> {
                    error("invalid type")
                }
            }
        }
    }

    override suspend fun set(key: String, value: String, expiration: Instant?) {
        lock.withLock {
            storage.set(key, RedisValue.String(value), expiration)
        }
        val event = UpdateEvent.Set(key, value, expiration)
        replication.send(event)
        updates.emit(event)
    }

    override suspend fun type(key: String): String {
        lock.withLock {
            val value = storage.get(key) ?: return "none"
            return when (value) {
                is RedisValue.String -> "string"
                is Stream -> "stream"
            }
        }
    }

    override suspend fun add(stream: String, id: StreamId, values: List<String>): StreamId {
        val added = lock.withLock {
            val value = storage.getOrInsert(stream) { Stream.empty() to null }
            when (value) {
                is Stream -> value.append(id, values)
                else -> error("invalid type")
            }
        }
        val event = UpdateEvent.StreamAdd(stream, added, values)
        replication.send(event)
        updates.emit(event)
        return added
    }

    override suspend fun range(
        stream: String,
        start: StreamId,
        inclusive: Boolean,
        end: StreamId,
        count: Int,
    ): List<Pair<StreamId, List<String>>> {
        lock.withLock {
            val value = storage.get(stream) ?: return listOf()
            return when (value) {
                is Stream -> value.range(start, inclusive, end)
                    .take(count)
                    .toList()
                else -> error("invalid type")
            }
        }
    }

    override suspend fun wait(): WaitBuilder =
        WaitBuilder(updates)
}
