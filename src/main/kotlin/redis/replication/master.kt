package redis.replication

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import redis.connection.ReplicaConnection
import redis.connection.receiveCommand
import redis.engine.RedisEngine
import redis.engine.UpdateEvent
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalEncodingApi::class)
@Suppress("MaxLineLength")
private val EMPTY_RDB: ByteArray = Base64.decode(
    "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==",
)

public fun masterReplication(
    context: CoroutineContext,
    engine: RedisEngine,
    state: ReplicationState,
): IncomingReplicaConnectionChannel {
    val channel = Channel<ReplicaConnection>()

    CoroutineScope(context + Dispatchers.Default)
        .launch { replicationLoop(channel, engine, state) }

    return IncomingReplicaConnectionChannel(channel)
}

private suspend fun replicationLoop(
    connections: ReceiveChannel<ReplicaConnection>,
    engine: RedisEngine,
    state: ReplicationState,
) {
    val replicas = mutableListOf<ReplicaConnection>()
    val replicationLog = engine.replicationChannel

    while (true) {
        select<Unit> {
            connections.onReceive { connection ->
                println("Received new replica")
                connection.initialize(state)
                replicas.add(connection)
                state.activeReplicasCount.update { it + 1 }
                state.replicaAcks.emit(state.replicaCount to 0)
            }
            replicationLog.onReceive { event ->
                if (replicas.isEmpty()) return@onReceive

                replicas.broadcast(event)
                state.replicationOffsetFlow.value = replicas[0].bytesWritten
            }
            state.acksRequest.onReceive {
                replicas.collectAcks(state)
            }
        }
    }
}

private suspend fun List<ReplicaConnection>.collectAcks(
    state: ReplicationState,
) {
    val command = listOf("replconf", "GETACK", "*")
    val target = state.replicationOffset

    coroutineScope {
        val acks = map { connection ->
            async {
                kotlin.runCatching {
                    val (_, _, rawOffset) = withTimeout(50.milliseconds) {
                        connection.sendCommand(command)
                        connection.receiveCommand()
                    }
                    val offset = rawOffset.toLong()

                    println("Collected ack from ${connection.id}")
                    offset >= target
                }.getOrElse {
                    false
                }
            }
        }
            .awaitAll()
            .count { it }

        state.replicaAcks.emit(acks to target)
    }
}

private suspend fun List<ReplicaConnection>.broadcast(event: UpdateEvent) {
    val command = when (event) {
        is UpdateEvent.Set -> buildList<String> {
            add("set")
            add(event.key)
            add(event.value)
            if (event.expiresAt != null) {
                add("pxat")
                add(event.expiresAt.toEpochMilliseconds().toString())
            }
        }

        is UpdateEvent.StreamAdd -> listOf(
            "xadd",
            event.key,
            event.id.toString(),
        ) + event.values
    }

    coroutineScope {
        for (connection in this@broadcast) {
            launch {
                connection.sendCommand(command)
            }
        }
    }
}

private suspend fun ReplicaConnection.initialize(replicationState: ReplicationState) {
    sendSimple("FULLRESYNC ${replicationState.replicationId} 0")
    sendRdb(EMPTY_RDB)
    println("sent rdb")
}
