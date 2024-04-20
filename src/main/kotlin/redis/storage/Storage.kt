package redis.storage

import kotlinx.datetime.Instant
import redis.storage.values.RedisValue

public interface Storage {
    public fun keys(): Sequence<String>
    public fun get(key: String): RedisValue?
    public fun set(key: String, value: RedisValue, expiration: Instant?)
    public fun getAux(key: String): String?
    public fun setAux(key: String, value: String)

    public fun getOrInsert(key: String, insert: () -> Pair<RedisValue, Instant?>): RedisValue
    public fun delete(key: String)
    public fun flush() { }
}
