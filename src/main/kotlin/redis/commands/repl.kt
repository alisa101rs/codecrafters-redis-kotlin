package redis.commands

import kotlinx.coroutines.withTimeout
import redis.replication.ReplicationState
import redis.response.NumberResponse
import redis.response.Response
import redis.response.SimpleResponse
import redis.response.Upgrade
import redis.routing.Command
import redis.routing.options.arg
import redis.routing.options.connectionState
import redis.routing.options.flag
import redis.routing.options.multiFlag
import redis.routing.options.resource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public class Info : Command() {
    private val replicationState by resource<ReplicationState>()
    private val section by arg(1)

    override suspend fun handle(): Response {
        check(section == "replication") { "other sections are not supported yet" }

        val response = """
            # Replication
            role:${replicationState.role}
            master_replid:${replicationState.replicationId}
            master_repl_offset:${replicationState.replicationOffset}
        """.trimIndent()

        return SimpleResponse(response)
    }
}

public class Replconf : Command() {
    private val state by connectionState()
    private val listeningPort by flag("listening-port") { it.toInt() }
    private val capabilities by multiFlag("capa")

    override suspend fun handle(): Response {
        state.listeningPort = listeningPort
        state.capabilities.addAll(capabilities)

        return SimpleResponse("OK")
    }
}

public class Psync : Command() {
    private val replicationId by arg(1)
    private val offset by arg(2) { it.toInt() }

    override suspend fun handle(): Response =
        Upgrade
}

public class Wait : Command() {
    private val state by resource<ReplicationState>()
    private val replicaCount by arg(1) { it.toInt() }
    private val timeout by arg(2) { it.toInt().takeIf { n -> n != 0 }?.milliseconds ?: Duration.INFINITE }

    override suspend fun handle(): Response {
        val currentOffset = state.replicationOffset
        val targetReplicas = min(replicaCount, state.replicaCount)
        if (targetReplicas == 0) return NumberResponse(0)

        var acks = 0

        try {
            withTimeout(timeout) {
                val (replicas, offset) = state.replicaAcks.replayCache.last()
                if (offset >= currentOffset) { acks = replicas }
                if (acks >= targetReplicas) { throw CancellationException() }

                state.acksRequest.send(Unit)
                state.replicaAcks.collect { (replicas, offset) ->
                    if (offset < currentOffset) return@collect

                    acks = max(acks, replicas)

                    if (acks >= targetReplicas) { throw CancellationException() }
                }
            }
        } catch (_: CancellationException) { }

        return NumberResponse(acks)
    }
}
