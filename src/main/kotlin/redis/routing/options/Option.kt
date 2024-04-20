package redis.routing.options

import redis.request.Request
import redis.routing.Command
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public interface Option {
    public fun process(request: Request)
}

public interface OptionWithValue<T> :
    Option,
    ReadOnlyProperty<Command, T>,
    PropertyDelegateProvider<Command, ReadOnlyProperty<Command, T>> {
    public val value: T

    override operator fun provideDelegate(
        thisRef: Command,
        property: KProperty<*>,
    ): ReadOnlyProperty<Command, T> {
        thisRef.registerOption(this)
        return this
    }

    override fun getValue(thisRef: Command, property: KProperty<*>): T = value
}
