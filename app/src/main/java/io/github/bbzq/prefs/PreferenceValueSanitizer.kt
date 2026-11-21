package io.github.bbzq.prefs

import java.util.LinkedHashSet

internal fun safeStringSet(values: Iterable<*>): MutableSet<String> {
    val result = LinkedHashSet<String>()
    values.forEach { result += it.toString() }
    return result
}

internal fun safeStringSetOrNull(values: Iterable<*>?): MutableSet<String>? =
    values?.let(::safeStringSet)
