package redis.commands

import redis.engine.RedisEngine
import redis.response.Response
import redis.response.SimpleResponse
import redis.routing.Command
import redis.routing.options.arg
import redis.routing.options.resource

public class Ping : Command() {
    override suspend fun handle(): Response =
        SimpleResponse("PONG")
}

public class Echo : Command() {
    private val message by arg(1)

    override suspend fun handle(): Response =
        SimpleResponse(message)
}

public class Type : Command() {
    private val key by arg(1)
    private val engine by resource<RedisEngine>()

    override suspend fun handle(): Response =
        SimpleResponse(engine.type(key))
}
