package redis.replication

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random
import kotlin.text.HexFormat
import kotlin.text.toHexString

public class ReplicationState private constructor(public val role: Role) {
    public val replicaAcks: MutableSharedFlow<Pair<Int, Long>> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val activeReplicasCount: MutableStateFlow<Int> = MutableStateFlow(0)

    internal val replicationOffsetFlow: MutableStateFlow<Long> = MutableStateFlow(0)
    private val replid: MutableStateFlow<String> = MutableStateFlow("")
    internal val acksRequest: Channel<Unit> = Channel(0, onBufferOverflow = BufferOverflow.DROP_LATEST)

    public var replicationId: String
        get() = replid.value
        set(value) {
            replid.value = value
        }

    public val replicationOffset: Long
        get() = replicationOffsetFlow.value

    public val replicaCount: Int
        get() = activeReplicasCount.value

    public companion object {
        public fun master(): ReplicationState = ReplicationState(Role.Master)
            .apply {
                replicationId = generateReplicationId()
            }

        public fun replica(): ReplicationState = ReplicationState(Role.Slave)
    }
}

public enum class Role {
    Master,
    Slave,
    ;

    override fun toString(): String =
        name.lowercase()
}

@OptIn(ExperimentalStdlibApi::class)
private fun generateReplicationId(): String {
    @Suppress("MagicNumber")

    return Random.Default.nextBytes(20).toHexString(format = HexFormat.Default)
}
