package com.supernutrition.platform.photoandroid

import com.supernutrition.domain.photoanalysis.ImageOrientation

/** Reads only EXIF orientation; all other metadata is discarded. */
internal fun ByteArray.exifOrientation(): ImageOrientation =
    if (size >= JPEG_HEADER_SIZE && this[0] == MARKER_PREFIX_BYTE && this[1] == JPEG_START_BYTE) {
        scanJpegSegments()
    } else {
        ImageOrientation.UP
    }

private fun ByteArray.scanJpegSegments(): ImageOrientation {
    var index = JPEG_HEADER_SIZE
    while (index + 3 < size && this[index] == MARKER_PREFIX_BYTE) {
        val markerStart = skipMarkerPrefix(index)
        if (markerStart >= size) return ImageOrientation.UP
        val marker = this[markerStart].toInt() and 0xff
        if (marker == JPEG_SCAN_MARKER || marker == JPEG_END_MARKER) return ImageOrientation.UP
        val segment = readJpegSegment(markerStart, marker) ?: return ImageOrientation.UP
        if (marker == EXIF_MARKER && ascii(segment.payloadStart, EXIF_HEADER)) {
            return readTiffOrientation(segment.payloadStart + EXIF_HEADER.length)
                ?: ImageOrientation.UP
        }
        index = segment.nextIndex
    }
    return ImageOrientation.UP
}

private fun ByteArray.skipMarkerPrefix(index: Int): Int {
    var markerStart = index
    while (markerStart < size && this[markerStart] == MARKER_PREFIX_BYTE) markerStart++
    return markerStart
}

private fun ByteArray.readJpegSegment(markerStart: Int, marker: Int): JpegSegment? {
    if (marker == JPEG_START_MARKER || marker in JPEG_RESTART_MARKERS) {
        return JpegSegment(markerStart + 1, markerStart + 1)
    }
    val segmentLength = readUnsignedShort(markerStart + 1, littleEndian = false)
    if (segmentLength < 2 || markerStart > size - segmentLength - 1) return null
    return JpegSegment(markerStart + 3, markerStart + 1 + segmentLength)
}

private fun ByteArray.readTiffOrientation(tiffStart: Int): ImageOrientation? {
    val header = readTiffHeader(tiffStart) ?: return null
    val entryCount = readUnsignedShort(header.ifdStart, header.littleEndian)
    if (entryCount < 0) return null
    return findOrientationEntry(header.ifdStart, entryCount, header.littleEndian)
}

private fun ByteArray.readTiffHeader(tiffStart: Int): TiffHeader? {
    if (tiffStart < 0 || tiffStart > size - TIFF_HEADER_SIZE) return null
    val littleEndian = when {
        ascii(tiffStart, "II") -> true
        ascii(tiffStart, "MM") -> false
        else -> return null
    }
    if (readUnsignedShort(tiffStart + 2, littleEndian) != TIFF_MAGIC) return null
    val ifdOffset = readInt(tiffStart + 4, littleEndian)
    val ifdStart = tiffStart + ifdOffset
    if (ifdOffset < TIFF_HEADER_SIZE || ifdStart < tiffStart || ifdStart > size - 2) {
        return null
    }
    return TiffHeader(littleEndian, ifdStart)
}

private fun ByteArray.findOrientationEntry(ifdStart: Int, entryCount: Int, littleEndian: Boolean): ImageOrientation? {
    for (entry in 0 until entryCount) {
        val entryStart = ifdStart + 2 + entry * TIFF_ENTRY_SIZE
        if (entryStart < ifdStart || entryStart > size - TIFF_ENTRY_SIZE) return null
        if (readUnsignedShort(entryStart, littleEndian) != ORIENTATION_TAG) continue
        return readOrientationValue(entryStart, littleEndian)
    }
    return null
}

private fun ByteArray.readOrientationValue(entryStart: Int, littleEndian: Boolean): ImageOrientation? {
    if (readUnsignedShort(entryStart + 2, littleEndian) != SHORT_TYPE ||
        readInt(entryStart + 4, littleEndian) != 1
    ) {
        return null
    }
    return when (readUnsignedShort(entryStart + 8, littleEndian)) {
        3 -> ImageOrientation.DOWN
        6 -> ImageOrientation.RIGHT
        8 -> ImageOrientation.LEFT
        else -> ImageOrientation.UP
    }
}

private fun ByteArray.ascii(start: Int, value: String): Boolean {
    if (start < 0 || start > size - value.length) return false
    return value.indices.all { this[start + it].toInt() and 0xff == value[it].code }
}

private fun ByteArray.readUnsignedShort(offset: Int, littleEndian: Boolean): Int {
    if (offset < 0 || offset > size - 2) return -1
    val first = this[offset].toInt() and 0xff
    val second = this[offset + 1].toInt() and 0xff
    return if (littleEndian) first or (second shl 8) else (first shl 8) or second
}

private fun ByteArray.readInt(offset: Int, littleEndian: Boolean): Int {
    if (offset < 0 || offset > size - 4) return -1
    val first = this[offset].toInt() and 0xff
    val second = this[offset + 1].toInt() and 0xff
    val third = this[offset + 2].toInt() and 0xff
    val fourth = this[offset + 3].toInt() and 0xff
    return if (littleEndian) {
        first or (second shl 8) or (third shl 16) or (fourth shl 24)
    } else {
        (first shl 24) or (second shl 16) or (third shl 8) or fourth
    }
}

private data class JpegSegment(val payloadStart: Int, val nextIndex: Int)

private data class TiffHeader(val littleEndian: Boolean, val ifdStart: Int)

private const val JPEG_HEADER_SIZE = 2
private const val TIFF_HEADER_SIZE = 8
private const val TIFF_ENTRY_SIZE = 12
private const val TIFF_MAGIC = 42
private const val ORIENTATION_TAG = 0x0112
private const val SHORT_TYPE = 3
private const val EXIF_MARKER = 0xe1
private const val JPEG_START_MARKER = 0xd8
private const val JPEG_SCAN_MARKER = 0xda
private const val JPEG_END_MARKER = 0xd9
private const val MARKER_PREFIX_BYTE: Byte = 0xff.toByte()
private const val JPEG_START_BYTE: Byte = 0xd8.toByte()
private const val EXIF_HEADER = "Exif\u0000\u0000"
private val JPEG_RESTART_MARKERS = 0xd0..0xd7
