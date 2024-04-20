package redis.storage

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import redis.storage.values.RedisValue

public class Memory(private val clock: Clock = Clock.System) : Storage {
    private val aux: MutableMap<String, String> = mutableMapOf()
    private val data: MutableMap<String, Pair<RedisValue, Instant?>> = mutableMapOf()

    override fun keys(): Sequence<String> =
        data.keys.asSequence()

    override fun get(key: String): RedisValue? {
        val (value, expiresAt) = data[key] ?: return null

        if (expiresAt != null && clock.now() >= expiresAt) {
            delete(key)
            return null
        }

        return value
    }

    override fun set(key: String, value: RedisValue, expiration: Instant?) {
        data[key] = value to expiration
    }

    override fun getAux(key: String): String? =
        aux[key]

    override fun setAux(key: String, value: String) {
        aux[key] = value
    }

    override fun getOrInsert(key: String, insert: () -> Pair<RedisValue, Instant?>): RedisValue =
        data.getOrPut(key) {
            insert()
        }.first

    override fun delete(key: String) {
        data.remove(key)
    }
}
