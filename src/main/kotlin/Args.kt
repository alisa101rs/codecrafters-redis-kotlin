import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.varargValues
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.ktor.network.sockets.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

public class Args : CliktCommand() {
    public val port: Int by option().int().default(6379)
    public val replicaof: InetSocketAddress? by option().varargValues(min = 2, max = 2)
        .transformAll { argPairs ->
            if (argPairs.isEmpty()) return@transformAll null
            val (it) = argPairs
            InetSocketAddress(it[0], it[1].toInt())
        }

    public val dir: Path? by option().path()
    public val dbfilename: String? by option()
    public val dbFile: Path?
        get() {
            val filename = dbfilename ?: return null
            val dir = dir ?: Path(".")

            return dir / filename
        }

    override fun toString(): String =
        "Args(port=$port, dir=$dir, dbfilename=$dbfilename, replicaof=$replicaof)"

    @Suppress("EmptyFunctionBlock")
    override fun run() {}
}
