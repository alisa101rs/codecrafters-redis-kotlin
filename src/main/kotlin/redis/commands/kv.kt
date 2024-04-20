package redis.commands

import kotlinx.datetime.Clock
import redis.engine.RedisEngine
import redis.response.NullResponse
import redis.response.Response
import redis.response.SimpleResponse
import redis.routing.Command
import redis.routing.options.arg
import redis.routing.options.flag
import redis.routing.options.resource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public class Get : Command() {
    private val key: String by arg(1)
    private val engine by resource<RedisEngine>()

    override suspend fun handle(): Response {
        val value = engine.get(key) ?: return NullResponse

        return SimpleResponse(value)
    }
}

public class Set(private val clock: Clock = Clock.System) : Command() {
    private val key: String by arg(1)
    private val value: String by arg(2)
    private val expirationMs: Duration? by flag("px") { it.toInt().milliseconds }
    private val expirationSec: Duration? by flag("ex") { it.toInt().seconds }

    private val engine by resource<RedisEngine>()

    override suspend fun handle(): Response {
        val expiration = expirationMs ?: expirationSec
        val expiresAt = expiration?.let { clock.now() + it }
        engine.set(key, value, expiresAt)

        return SimpleResponse("OK")
    }
}
