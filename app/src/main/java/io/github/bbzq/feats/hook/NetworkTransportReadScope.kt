package io.github.bbzq.feats.hook

internal object NetworkTransportReadScope {
    private val reading = ThreadLocal<Boolean>()
    val isActive: Boolean get() = reading.get() == true

    fun <T> read(block: () -> T): T {
        val previous = reading.get()
        reading.set(true)
        return try {
            block()
        } finally {
            if (previous == null) reading.remove() else reading.set(previous)
        }
    }
}
