@file:OptIn(InternalAPI::class)
@file:Suppress("TooManyFunctions")

package redis.routing.options

import io.ktor.utils.io.InternalAPI
import redis.connection.ClientConnectionState
import redis.request.Request
import redis.routing.Command
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UnusedReceiverParameter")
public fun Command.arg(position: Int): OptionWithValue<String> {
    check(position > 0)

    return object : OptionWithValue<String> {
        override var value: String by NullableLateinit()

        override fun process(request: Request) {
            val v =
                request.args.getOrNull(position - 1) ?: error("Argument at position $position is missing")

            value = v
        }
    }
}

@Suppress("UnusedReceiverParameter")
public fun <T> Command.arg(position: Int, transform: (String) -> T): OptionWithValue<T> =
    arg(position).transform(transform)

@Suppress("UnusedReceiverParameter")
public fun Command.flag(name: String): OptionWithValue<String?> =
    object : OptionWithValue<String?> {
        override var value: String? by NullableLateinit()

        override fun process(request: Request) {
            value = null
            val position =
                request.args.indexOfFirst { it.equals(name, ignoreCase = true) }.takeUnless { it == -1 } ?: return
            val v = request.args.getOrNull(position + 1) ?: error("missing value for flag")

            value = v
        }
    }

@Suppress("UnusedReceiverParameter")
public fun Command.flag(name: String, values: Int): OptionWithValue<List<String>> {
    return object : OptionWithValue<List<String>> {
        override var value: List<String> = listOf()

        override fun provideDelegate(
            thisRef: Command,
            property: KProperty<*>,
        ): ReadOnlyProperty<Command, List<String>> {
            thisRef.registerOption(this)
            return this
        }

        override fun process(request: Request) {
            val position =
                request.args.indexOfFirst { it.equals(name, ignoreCase = true) }.takeUnless { it == -1 } ?: return
            value = request.args.subList(
                position + 1,
                request.args.size,
            ).take(values)
        }
    }
}

@Suppress("UnusedReceiverParameter")
public fun Command.multiFlag(name: String): OptionWithValue<List<String>> =
    object : OptionWithValue<List<String>> {
        override var value: List<String> = listOf()

        override fun provideDelegate(
            thisRef: Command,
            property: KProperty<*>,
        ): ReadOnlyProperty<Command, List<String>> {
            thisRef.registerOption(this)
            return this
        }

        override fun process(request: Request) {
            value = request.args.mapIndexedNotNull { index, s ->
                if (!s.equals(name, ignoreCase = true)) return@mapIndexedNotNull null
                val value = request.args.getOrNull(index + 1) ?: error("missing value for flag")
                value
            }
        }
    }

@Suppress("UnusedReceiverParameter")
public fun Command.vararg(startPosition: Int): OptionWithValue<List<String>> =
    object : OptionWithValue<List<String>> {
        override var value: List<String> = listOf()

        override fun provideDelegate(
            thisRef: Command,
            property: KProperty<*>,
        ): ReadOnlyProperty<Command, List<String>> {
            thisRef.registerOption(this)
            return this
        }

        override fun process(request: Request) {
            value = request.args.subList(startPosition - 1, request.args.size)
        }
    }

@Suppress("UnusedReceiverParameter")
public fun <T> Command.flag(name: String, transform: (String) -> T): OptionWithValue<T?> =
    flag(name).transformNullable(transform)

@Suppress("UnusedReceiverParameter")
public inline fun <reified T : Any> Command.resource(): OptionWithValue<T> {
    val type = T::class.java

    return object : OptionWithValue<T> {
        override var value: T by NullableLateinit()

        override fun process(request: Request) {
            val v = request.resources[type] ?: error("Resource of type $type is not registered")
            value = v as T
        }
    }
}

@Suppress("UnusedReceiverParameter")
public fun Command.connectionState(): OptionWithValue<ClientConnectionState> =
    object : OptionWithValue<ClientConnectionState> {
        override var value: ClientConnectionState by NullableLateinit()

        override fun process(request: Request) {
            value = request.connectionState
        }
    }

public fun <T, U> OptionWithValue<T>.transform(transformer: (T) -> U): OptionWithValue<U> =
    object : OptionWithValue<U> {
        override var value: U by NullableLateinit()

        override fun process(request: Request) {
            this@transform.process(request)
            value = transformer(this@transform.value)
        }
    }

public fun <T, U> OptionWithValue<T?>.transformNullable(transformer: (T) -> U): OptionWithValue<U?> =
    object : OptionWithValue<U?> {
        override var value: U? by NullableLateinit()

        override fun process(request: Request) {
            this@transformNullable.process(request)
            value = this@transformNullable.value?.let { transformer(it) }
        }
    }

public fun <T> OptionWithValue<T?>.default(default: T): OptionWithValue<T> =
    object : OptionWithValue<T> {
        override var value: T = default

        override fun process(request: Request) {
            this@default.process(request)
            value = this@default.value ?: default
        }
    }

@InternalAPI
public class NullableLateinit<T> : ReadWriteProperty<Any, T> {
    private object UNINITIALIZED

    private var value: Any? = UNINITIALIZED

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (value === UNINITIALIZED) error("Cannot read from option delegate before processing request")

        try {
            @Suppress("UNCHECKED_CAST")
            return value as T
        } catch (_: ClassCastException) {
            error("Value is of invalid type")
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        this.value = value
    }
}
