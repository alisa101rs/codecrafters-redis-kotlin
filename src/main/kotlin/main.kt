import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.tcpNoDelay
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import redis.commands.Echo
import redis.commands.Get
import redis.commands.Info
import redis.commands.Ping
import redis.commands.Psync
import redis.commands.Replconf
import redis.commands.Set
import redis.commands.Type
import redis.commands.Wait
import redis.commands.Xadd
import redis.commands.Xrange
import redis.commands.Xread
import redis.connection.ClientConnection
import redis.connection.MasterConnection
import redis.connection.ReplicaConnection
import redis.connection.ping
import redis.connection.replConf
import redis.connection.startPsync
import redis.engine.RedisEngine
import redis.replication.IncomingReplicaConnectionChannel
import redis.replication.ReplicationState
import redis.replication.masterReplication
import redis.replication.startReplication
import redis.request.Request
import redis.response.Upgrade
import redis.routing.Router
import redis.storage.Memory
import kotlin.coroutines.CoroutineContext

public suspend fun main(argv: Array<String>) {
    val args = Args()
    args.main(argv)

    coroutineScope {
        val serverScope = SupervisorJob(coroutineContext.job)
        val listener = aSocket(ActorSelectorManager(serverScope)).tcp().tcpNoDelay().bind(
            InetSocketAddress("0.0.0.0", args.port),
        )

        val router = if (args.replicaof != null) {
            replica(SupervisorJob(coroutineContext.job), args.port, checkNotNull(args.replicaof))
        } else {
            master(SupervisorJob(coroutineContext.job))
        }

        println("Listening on ${listener.localAddress}")

        while (true) {
            val client = listener.accept()
            println("Accepted client: ${client.remoteAddress}")
            serve(client, router)
        }
    }
}

private fun master(context: CoroutineContext): Router {
    val replicationState = ReplicationState.master()
    val engine = RedisEngine(Memory())

    val clients = masterReplication(context, engine, replicationState)

    return Router.define {
        resource(engine)
        resource(replicationState)
        resource(clients)

        route("ping") { Ping() }
        route("echo") { Echo() }
        route("get") { Get() }
        route("set") { Set() }
        route("info") { Info() }
        route("replconf") { Replconf() }
        route("psync") { Psync() }
        route("wait") { Wait() }
        route("type") { Type() }
        route("xadd") { Xadd() }
        route("xrange") { Xrange() }
        route("xread") { Xread() }
    }
}

private suspend fun replica(context: CoroutineContext, myPort: Int, replicaOf: InetSocketAddress): Router {
    val replicationState = ReplicationState.replica()
    val engine = RedisEngine(Memory())
    val masterConnection = handshake(context, replicaOf, myPort)

    startReplication(context, masterConnection, engine, replicationState)

    return Router.define {
        resource(engine)
        resource(replicationState)

        route("ping") { Ping() }
        route("echo") { Echo() }
        route("get") { Get() }
        route("info") { Info() }
        route("type") { Type() }
        route("xrange") { Xrange() }
        route("xread") { Xread() }
    }
}

private suspend fun handshake(
    context: CoroutineContext,
    address: SocketAddress,
    myPort: Int,
): MasterConnection {
    val connect = flow {
        emit(aSocket(ActorSelectorManager(context)).tcp().tcpNoDelay().connect(address))
    }
        .retry(10) { er -> er is IOException }
        .single()

    val connection = MasterConnection(connect)

    connection.ping()
    connection.replConf(
        listeningPort = myPort,
        capabilities = listOf("psync2"),
    )
    connection.startPsync()

    return connection
}

private fun CoroutineScope.serve(
    rawConnection: Socket,
    router: Router,
) {
    val connection = ClientConnection(rawConnection)
    val commands = connection.commands(coroutineContext + CoroutineName("commands#${connection.remoteAddress}"))

    launch {
        for (raw in commands) {
            println("Accepted command: $raw")
            val request = Request.fromCommands(raw, connection.state)
            val response = router.handle(request)
            connection.respond(response)

            if (response is Upgrade) {
                val clients = router.resource<IncomingReplicaConnectionChannel>()
                clients.send(connection.upgradeToReplica())
                break
            }
        }
        commands.cancel()
    }
}
