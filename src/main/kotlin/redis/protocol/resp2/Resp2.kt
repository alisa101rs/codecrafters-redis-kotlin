package redis.protocol.resp2

public sealed class Resp2 {
    public data object Nil : Resp2()
    public data class String(val value: kotlin.String) : Resp2() {
        public companion object
    }
    public data class Number(val value: Int) : Resp2() {
        public companion object
    }
    public data class List(val values: kotlin.collections.List<Resp2>) : Resp2() {
        public companion object {
            public operator fun invoke(vararg values: Resp2): List =
                List(listOf(*values))
            public operator fun invoke(values: kotlin.collections.List<kotlin.String>): List =
                List(values.map(Resp2::String))
        }
    }

    public data class Error(val message: kotlin.String) : Resp2()

    public companion object
}
