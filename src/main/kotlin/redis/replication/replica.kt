package redis.replication

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import redis.commands.replica.Ack
import redis.commands.replica.Ping
import redis.commands.replica.Set
import redis.commands.replica.Xadd
import redis.connection.MasterConnection
import redis.connection.receiveCommand
import redis.connection.receiveSimpleResponse
import redis.engine.RedisEngine
import redis.request.Request
import redis.routing.Router
import kotlin.coroutines.CoroutineContext

internal fun startReplication(
    scope: CoroutineContext,
    connection: MasterConnection,
    engine: RedisEngine,
    replicationState: ReplicationState,
) {
    CoroutineScope(scope + Dispatchers.Default)
        .launch { replicationLoop(connection, engine, replicationState) }
}

private suspend fun replicationLoop(
    connection: MasterConnection,
    engine: RedisEngine,
    replicationState: ReplicationState,
) {
    val (syncType, masterReplicationId, offsetId) = connection.receiveSimpleResponse().split(" ")
    check(syncType == "FULLRESYNC") { "only support full resynchronization right now" }
    check(offsetId == "0") { "only support replication from the start right now" }
    replicationState.replicationId = masterReplicationId

    println("Starting full resynchronization")

    val replicationOffsetFlow = replicationState.replicationOffsetFlow

    replicationOffsetFlow.value = offsetId.toLong()

    // TODO: read rdb into memory
    @Suppress("UnusedPrivateProperty")
    val rdbState = connection.receiveRdb()
    println("Received RDB")

    val handshakeSize = connection.processed

    val router = Router.define {
        resource(engine)
        resource(replicationState)

        route("set") { Set() }
        route("ping") { Ping() }
        route("replconf") { Ack() }
        route("xadd") { Xadd() }
    }

    while (true) {
        replicationState.replicationOffsetFlow.value = connection.processed - handshakeSize
        val raw = connection.receiveCommand()
        println("Received request from master: $raw")
        val request = Request.fromCommands(raw, connection.state)
        val response = router.handle(request)
        connection.respond(response)
    }
}
