@file:OptIn(InternalAPI::class)

package redis.routing

import io.ktor.utils.io.InternalAPI
import redis.request.Request
import redis.response.ErrorResponse
import redis.response.Response

public typealias HandlerBuilder = suspend () -> Handler

public class Router {
    @InternalAPI
    public val resources: MutableMap<Class<*>, Any> = mutableMapOf()
    internal val routes: MutableMap<String, HandlerBuilder> = mutableMapOf()

    public inline fun<reified T : Any> resource(): T =
        checkNotNull(resources[T::class.java]) { "Resource not registered" } as T

    public suspend fun handle(request: Request): Response = runCatching {
        val handler = routes[request.command] ?: error("unknown command: ${request.command}")

        return handler()
            .handle(request.copy(resources = resources))
    }.getOrElse {
        ErrorResponse("${it.message}")
    }

    public companion object {
        public inline fun define(block: RouterBuilder.() -> Unit): Router =
            RouterBuilder()
                .run {
                    block()
                    build()
                }
    }
}

public class RouterBuilder {
    @InternalAPI
    public val router: Router = Router()
    public fun build(): Router = router

    public fun route(command: String, builder: HandlerBuilder) {
        router.routes[command.lowercase()] = { builder() }
    }
    public inline fun <reified T : Any> resource(value: T) {
        router.resources[T::class.java] = value as Any
    }
}
