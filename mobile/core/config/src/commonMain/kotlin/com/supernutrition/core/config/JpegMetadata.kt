package com.supernutrition.core.config

/** Removes JPEG application segments from an already encoded payload. */
fun ByteArray.withoutJpegApplicationMetadata(): ByteArray {
    if (size < 4 || this[0] != 0xff.toByte() || this[1] != 0xd8.toByte()) return this

    val output = ByteArray(size)
    var outputSize = 0
    fun appendRange(start: Int, endExclusive: Int) {
        val length = endExclusive - start
        copyInto(output, outputSize, start, endExclusive)
        outputSize += length
    }

    appendRange(0, 2)
    var index = 2
    while (index < size) {
        val segment = jpegSegment(index)
        if (segment.keep) appendRange(index, segment.endExclusive)
        index = if (segment.terminal) size else segment.endExclusive
    }
    return output.copyOf(outputSize)
}

private fun ByteArray.jpegSegment(start: Int): JpegSegment {
    var markerIndex = start
    while (markerIndex < size && this[markerIndex] == 0xff.toByte()) markerIndex++
    if (markerIndex >= size) return JpegSegment(size, keep = true, terminal = true)

    val marker = this[markerIndex].toInt() and 0xff
    return when {
        marker == 0xda -> JpegSegment(size, keep = true, terminal = true)

        marker == 0xd8 || marker == 0xd9 || marker in 0xd0..0xd7 ->
            JpegSegment(markerIndex + 1, keep = true, terminal = false)

        markerIndex + 2 >= size -> JpegSegment(size, keep = true, terminal = true)

        else -> {
            val segmentLength = ((this[markerIndex + 1].toInt() and 0xff) shl 8) or
                (this[markerIndex + 2].toInt() and 0xff)
            val segmentEnd = markerIndex + 1 + segmentLength
            if (segmentLength < 2 || segmentEnd > size) {
                JpegSegment(size, keep = true, terminal = true)
            } else {
                JpegSegment(segmentEnd, keep = marker !in 0xe0..0xef, terminal = false)
            }
        }
    }
}

private data class JpegSegment(val endExclusive: Int, val keep: Boolean, val terminal: Boolean)
