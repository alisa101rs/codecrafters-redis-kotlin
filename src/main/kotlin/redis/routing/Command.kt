package redis.routing

import redis.request.Request
import redis.response.Response
import redis.routing.options.Option

public abstract class Command : Handler() {
    private val options: MutableList<Option> = mutableListOf()
    internal fun registerOption(option: Option) {
        options.add(option)
    }

    override suspend fun handle(request: Request): Response {
        for (option in options) {
            option.process(request)
        }
        return handle()
    }

    public abstract suspend fun handle(): Response
}
