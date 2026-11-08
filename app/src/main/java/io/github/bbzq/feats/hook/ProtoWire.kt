package io.github.bbzq.feats.hook

import java.io.ByteArrayOutputStream

/** Minimal protobuf wire writer; zero-valued scalars and empty strings are omitted like proto3. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun string(field: Int, value: String) {
        if (value.isEmpty()) return
        message(field, value.toByteArray(Charsets.UTF_8))
    }

    fun message(field: Int, value: ByteArray) {
        tag(field, 2)
        varint(value.size.toLong())
        out.write(value)
    }

    fun int(field: Int, value: Long) {
        if (value == 0L) return
        intAlways(field, value)
    }

    fun intAlways(field: Int, value: Long) {
        tag(field, 0)
        varint(value)
    }

    fun bool(field: Int, value: Boolean) {
        if (value) int(field, 1)
    }

    fun double(field: Int, value: Double) {
        if (value == 0.0) return
        tag(field, 1)
        val bits = java.lang.Double.doubleToRawLongBits(value)
        for (i in 0 until 8) out.write((bits ushr (8 * i)).toInt() and 0xFF)
    }

    /** Appends already-encoded wire bytes (for example untouched original fields). */
    fun raw(value: ByteArray) {
        out.write(value)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    private fun varint(value: Long) {
        var remaining = value
        while (remaining and 0x7FL.inv() != 0L) {
            out.write(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        out.write(remaining.toInt())
    }
}

internal object ProtoWire {
    class Field(val number: Int, val wireType: Int, val varint: Long = 0L, val bytes: ByteArray = ByteArray(0)) {
        fun string(): String = String(bytes, Charsets.UTF_8)
    }

    /**
     * Parses top-level protobuf wire fields from [data]. Returns null if [data] is malformed.
     */
    fun readFields(data: ByteArray): List<Field>? {
        val result = ArrayList<Field>()
        var offset = 0
        while (offset < data.size) {
            var tag = 0L
            var shift = 0
            while (true) {
                if (offset >= data.size || shift > 63) return null
                val b = data[offset++].toInt() and 0xFF
                tag = tag or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) break
                shift += 7
            }
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 7).toInt()
            when (wireType) {
                0 -> {
                    var value = 0L
                    var valShift = 0
                    while (true) {
                        if (offset >= data.size || valShift > 63) return null
                        val b = data[offset++].toInt() and 0xFF
                        value = value or ((b and 0x7F).toLong() shl valShift)
                        if (b < 0x80) break
                        valShift += 7
                    }
                    result += Field(fieldNumber, wireType, varint = value)
                }
                1 -> {
                    if (offset + 8 > data.size) return null
                    result += Field(fieldNumber, wireType, bytes = data.copyOfRange(offset, offset + 8))
                    offset += 8
                }
                5 -> {
                    if (offset + 4 > data.size) return null
                    result += Field(fieldNumber, wireType, bytes = data.copyOfRange(offset, offset + 4))
                    offset += 4
                }
                2 -> {
                    var length = 0L
                    var lengthShift = 0
                    while (true) {
                        if (offset >= data.size || lengthShift > 63) return null
                        val b = data[offset++].toInt() and 0xFF
                        length = length or ((b and 0x7F).toLong() shl lengthShift)
                        if (b < 0x80) break
                        lengthShift += 7
                    }
                    if (length < 0 || offset + length > data.size) return null
                    val end = offset + length.toInt()
                    result += Field(fieldNumber, wireType, bytes = data.copyOfRange(offset, end))
                    offset = end
                }
                else -> return null
            }
        }
        return result
    }

    /** Reads a `map<string, string>` entry message (`key` = field 1, `value` = field 2). */
    fun readStringMapEntry(entryBytes: ByteArray): Pair<String, String>? {
        val fields = readFields(entryBytes) ?: return null
        val key = fields.firstOrNull { it.number == 1 && it.wireType == 2 }?.string() ?: ""
        val value = fields.firstOrNull { it.number == 2 && it.wireType == 2 }?.string() ?: ""
        return key to value
    }

    /**
     * Returns [data] without any top-level field whose number is in [numbers], keeping the
     * remaining fields byte-for-byte. Returns null when [data] is not well-formed.
     */
    fun dropFields(data: ByteArray, numbers: Set<Int>): ByteArray? {
        val out = ByteArrayOutputStream(data.size)
        var offset = 0
        while (offset < data.size) {
            val start = offset
            var tag = 0L
            var shift = 0
            while (true) {
                if (offset >= data.size || shift > 63) return null
                val b = data[offset++].toInt() and 0xFF
                tag = tag or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) break
                shift += 7
            }
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> {
                    while (true) {
                        if (offset >= data.size) return null
                        if (data[offset++].toInt() and 0x80 == 0) break
                    }
                }
                1 -> offset += 8
                5 -> offset += 4
                2 -> {
                    var length = 0L
                    var lengthShift = 0
                    while (true) {
                        if (offset >= data.size || lengthShift > 63) return null
                        val b = data[offset++].toInt() and 0xFF
                        length = length or ((b and 0x7F).toLong() shl lengthShift)
                        if (b < 0x80) break
                        lengthShift += 7
                    }
                    if (length < 0 || offset + length > data.size) return null
                    offset += length.toInt()
                }
                else -> return null
            }
            if (offset > data.size) return null
            if (field !in numbers) out.write(data, start, offset - start)
        }
        return out.toByteArray()
    }
}
