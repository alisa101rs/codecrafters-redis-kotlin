@file:Suppress("FunctionNaming")

package redis.response

import redis.protocol.resp2.Resp2

public sealed class Response

public data object None : Response()
public data object Upgrade : Response()
public data class Resp2Response(val body: Resp2) : Response()

public fun SimpleResponse(body: String): Response = Resp2Response(Resp2.String(body))

public fun NumberResponse(value: Int): Response = Resp2Response(Resp2.Number(value))

public val NullResponse: Response = Resp2Response(Resp2.Nil)

public fun ArrayResponse(body: List<String>): Response = Resp2Response(
    Resp2.List(body),
)

public fun ErrorResponse(message: String): Response = Resp2Response(Resp2.Error(message))
