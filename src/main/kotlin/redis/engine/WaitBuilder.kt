package redis.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.coroutines.cancellation.CancellationException

public class WaitBuilder internal constructor(private val events: MutableSharedFlow<UpdateEvent>) {
    public suspend fun forKeys(vararg keys: String) {
        val k = keys.toSet()

        try {
            events.collect { event ->
                val key = when (event) {
                    is UpdateEvent.Set -> event.key
                    is UpdateEvent.StreamAdd -> event.key
                }

                if (key in k) throw CancellationException()
            }
        } catch (_: CancellationException) { }
    }
}
