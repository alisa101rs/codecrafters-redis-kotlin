package redis.routing

import redis.request.Request
import redis.response.Response

public abstract class Handler {
    public abstract suspend fun handle(request: Request): Response
}
