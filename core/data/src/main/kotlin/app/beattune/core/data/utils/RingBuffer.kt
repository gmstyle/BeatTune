package app.beattune.core.data.utils

import android.net.Uri
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

open class RingBuffer<T>(val size: Int, private val init: (index: Int) -> T) : Iterable<T> {
    private val list = MutableList(size, init)

    @get:Synchronized
    @set:Synchronized
    private var index = 0

    operator fun get(index: Int) = list.getOrNull(index)
    operator fun plusAssign(element: T) {
        list[index++ % size] = element
    }

    override fun iterator() = list.iterator()

    fun clear() = list.indices.forEach {
        list[it] = init(it)
    }
}

class UriCache<Key : Any, Meta>(size: Int = 16) {
    private val buffer = RingBuffer<CachedUri<Key, Meta>?>(size) { null }

    data class CachedUri<Key, Meta> @OptIn(ExperimentalTime::class)
    internal constructor(
        val key: Key,
        val meta: Meta,
        val uri: Uri,
        val validUntil: Instant?
    )

    @OptIn(ExperimentalTime::class)
    operator fun get(key: Key) = buffer.find {
        it != null && it.key == key && (it.validUntil == null || it.validUntil > Clock.System.now())
    }

    @OptIn(ExperimentalTime::class)
    fun push(
        key: Key,
        meta: Meta,
        uri: Uri,
        validUntil: Instant?
    ) {
        if (validUntil != null && validUntil <= Clock.System.now()) return

        buffer += CachedUri(key, meta, uri, validUntil)
    }

    fun clear() = buffer.clear()
}
