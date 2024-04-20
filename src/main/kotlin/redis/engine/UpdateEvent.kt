package redis.engine

import kotlinx.datetime.Instant
import redis.storage.values.StreamId

public sealed class UpdateEvent {
    public data class Set(
        val key: String,
        val value: String,
        val expiresAt: Instant?,
    ) : UpdateEvent()

    public data class StreamAdd(
        val key: String,
        val id: StreamId,
        val values: List<String>,
    ) : UpdateEvent()
}
