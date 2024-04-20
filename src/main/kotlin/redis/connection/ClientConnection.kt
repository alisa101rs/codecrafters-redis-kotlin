package redis.connection

import io.ktor.network.sockets.Socket
import redis.response.Response

public class ClientConnection(
    raw: Socket,
    public val state: ClientConnectionState = ClientConnectionState(address = raw.remoteAddress),
) : Connection(raw) {

    public suspend fun respond(response: Response) {
        response.writeTo(write)
    }

    public fun upgradeToReplica(): ReplicaConnection =
        ReplicaConnection(this)
}
