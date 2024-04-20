package redis.request

import redis.connection.ClientConnectionState

public data class Request(
    val command: String,
    val args: List<String>,
    val resources: Map<Class<*>, Any>,
    val connectionState: ClientConnectionState,
) {
    public companion object {
        public fun fromCommands(commands: List<String>, connection: ClientConnectionState): Request = Request(
            command = commands[0].lowercase(),
            args = commands.subList(1, commands.size),
            resources = mapOf(),
            connectionState = connection,
        )
    }
}
