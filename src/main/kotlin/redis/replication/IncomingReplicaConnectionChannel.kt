package redis.replication

import kotlinx.coroutines.channels.SendChannel
import redis.connection.ReplicaConnection

@JvmInline
public value class IncomingReplicaConnectionChannel(public val channel: SendChannel<ReplicaConnection>) :
    SendChannel<ReplicaConnection> by channel

