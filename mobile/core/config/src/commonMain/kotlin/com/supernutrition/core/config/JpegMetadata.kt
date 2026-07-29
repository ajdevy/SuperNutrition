package com.supernutrition.core.config

/** Removes JPEG application segments from an already encoded payload. */
fun ByteArray.withoutJpegApplicationMetadata(): ByteArray {
    if (size < 4 || this[0] != 0xff.toByte() || this[1] != 0xd8.toByte()) return this

    val output = ArrayList<Byte>(size)
    fun appendRange(start: Int, endExclusive: Int) {
        for (index in start until endExclusive) output += this[index]
    }

    appendRange(0, 2)
    var index = 2
    while (index < size) {
        val segmentStart = index
        if (this[index] != 0xff.toByte()) {
            appendRange(index, size)
            break
        }

        while (index < size && this[index] == 0xff.toByte()) index++
        if (index >= size) {
            appendRange(segmentStart, size)
            break
        }

        val marker = this[index].toInt() and 0xff
        if (marker == 0xda) {
            appendRange(segmentStart, size)
            break
        }
        if (marker == 0xd8 || marker == 0xd9 || marker in 0xd0..0xd7) {
            appendRange(segmentStart, index + 1)
            index++
            continue
        }

        if (index + 2 >= size) {
            appendRange(segmentStart, size)
            break
        }
        val segmentLength = ((this[index + 1].toInt() and 0xff) shl 8) or
            (this[index + 2].toInt() and 0xff)
        if (segmentLength < 2) {
            appendRange(segmentStart, size)
            break
        }
        val segmentEnd = index + 1 + segmentLength
        if (segmentEnd > size) {
            appendRange(segmentStart, size)
            break
        }

        if (marker !in 0xe1..0xef) appendRange(segmentStart, segmentEnd)
        index = segmentEnd
    }
    return ByteArray(output.size) { output[it] }
}
