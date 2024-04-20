package redis.storage.values

public sealed class RedisValue {
    public data class String(public val value: kotlin.String) : RedisValue()
}
