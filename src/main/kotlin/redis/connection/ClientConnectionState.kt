package redis.connection

import io.ktor.network.sockets.SocketAddress

public class ClientConnectionState(
    public val address: SocketAddress,
    public var listeningPort: Int? = null,
    public val capabilities: MutableList<String> = mutableListOf(),
)
