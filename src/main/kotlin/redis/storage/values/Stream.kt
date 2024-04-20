package redis.storage.values

import kotlinx.datetime.Clock
import java.util.TreeMap

public class Stream(
    public val entries: TreeMap<StreamId, List<kotlin.String>>,
    public var lastId: StreamId,
    public var firstId: StreamId?,
    public var maxDeletedEntryId: StreamId?,
    public var entriesAdded: Long,
) : RedisValue() {
    public companion object {
        public fun empty(): Stream =
            Stream(
                entries = TreeMap(),
                lastId = StreamId.MIN,
                firstId = null,
                maxDeletedEntryId = null,
                entriesAdded = 0,
            )
    }

    public fun append(id: StreamId, values: List<kotlin.String>): StreamId {
        if (id == StreamId.MIN) {
            error("ERR The ID specified in XADD must be greater than 0-0")
        }
        if (id <= lastId) {
            error("ERR The ID specified in XADD is equal or smaller than the target stream top item")
        }

        val nextId = id.allocate()

        entries[nextId] = values
        lastId = nextId
        if (firstId == null) {
            firstId = nextId
        }

        return nextId
    }

    public fun range(
        start: StreamId,
        inclusive: Boolean,
        end: StreamId? = null,
    ): Sequence<Pair<StreamId, List<kotlin.String>>> {
        val s = if (start == StreamId.MAX) {
            lastId
        } else {
            start
        }

        val e = if (end != null) {
            entries.subMap(s, inclusive, end, true)
        } else {
            entries.subMap(s, inclusive, StreamId.MAX, true)
        }.iterator()

        return sequence {
            while (true) {
                if (!e.hasNext()) {
                    return@sequence
                }
                val (id, values) = e.next()

                yield(id to values)
            }
        }
    }

    private fun StreamId.allocate(): StreamId {
        if (ts == Long.MAX_VALUE) return StreamId(Clock.System.now().toEpochMilliseconds(), 0)

        return when {
            ts == lastId.ts && c == Long.MAX_VALUE -> StreamId(ts, lastId.c + 1)
            c == Long.MAX_VALUE -> StreamId(ts, 0)
            else -> this
        }
    }
}

public data class StreamId(val ts: Long, val c: Long) : Comparable<StreamId> {
    public override operator fun compareTo(other: StreamId): Int =
        ts.compareTo(other.ts).takeUnless { it == 0 } ?: c.compareTo(other.c)

    public override fun toString(): String = "$ts-$c"

    public companion object {
        public val MIN: StreamId = StreamId(0, 0)
        public val MAX: StreamId = StreamId(Long.MAX_VALUE, Long.MAX_VALUE)
    }
}
