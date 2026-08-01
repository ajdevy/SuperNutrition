package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.isSha256Digest

typealias AttemptId = com.supernutrition.domain.inference.AttemptId
typealias PhotoRef = com.supernutrition.domain.inference.PhotoRef

class RequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "Request ID must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is RequestId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "RequestId(redacted)"
}

/** Port for attempt-scoped opaque photo leases. */
interface PhotoLeaseStorePort {
    fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef
    fun isActive(ref: PhotoRef): Boolean
    fun revoke(ref: PhotoRef): Boolean
    fun revokeAttempt(attemptId: AttemptId): Int
}

/** Single-reducer in-memory lease store retained under the original public name. */
class PhotoLeaseStore : PhotoLeaseStorePort {
    private val active = mutableSetOf<PhotoRef>()

    override fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef {
        val ref = PhotoRef.issue(attemptId, opaqueId)
        active += ref
        return ref
    }

    override fun isActive(ref: PhotoRef): Boolean = ref in active

    /** Returns false on repeated cleanup; repeated cleanup is still safe. */
    override fun revoke(ref: PhotoRef): Boolean = active.remove(ref)

    override fun revokeAttempt(attemptId: AttemptId): Int {
        val revoked = active.filterTo(mutableSetOf()) { it.attemptId == attemptId }
        active.removeAll(revoked)
        return revoked.size
    }
}

enum class ImageOrientation {
    UP,
    RIGHT,
    DOWN,
    LEFT,
}

/**
 * How the pixels are encoded, as opposed to what they contain.
 *
 * These three travel together everywhere and mean one thing: the format the
 * bytes are in. Grouping them is what lets a normalized input state its whole
 * requirement as a single equality against [NORMALIZED], rather than as three
 * separate invariants that a reader has to reassemble.
 */
class PhotoFormat(val orientation: ImageOrientation, val colorSpace: String, metadataKeys: Set<String>) {
    val metadataKeys: Set<String> = metadataKeys.toSet()

    fun copy(
        orientation: ImageOrientation = this.orientation,
        colorSpace: String = this.colorSpace,
        metadataKeys: Set<String> = this.metadataKeys,
    ): PhotoFormat = PhotoFormat(orientation, colorSpace, metadataKeys)

    override fun equals(other: Any?): Boolean = other is PhotoFormat &&
        orientation == other.orientation && colorSpace == other.colorSpace && metadataKeys == other.metadataKeys

    override fun hashCode(): Int {
        var result = orientation.hashCode()
        result = 31 * result + colorSpace.hashCode()
        return 31 * result + metadataKeys.hashCode()
    }

    override fun toString(): String = "PhotoFormat($orientation, $colorSpace, keys=${metadataKeys.size})"

    companion object {
        /** The only format a [NormalizedPhotoInput] may carry. */
        val NORMALIZED = PhotoFormat(ImageOrientation.UP, "sRGB", emptySet())
    }
}

/** Byte values are copied at the boundary and on every read. */
class RawPhoto(val ref: PhotoRef, bytes: ByteArray, val width: Int, val height: Int, val format: PhotoFormat) {
    private val storedBytes = bytes.copyOf()
    val bytes: ByteArray get() = storedBytes.copyOf()

    val orientation: ImageOrientation get() = format.orientation
    val colorSpace: String get() = format.colorSpace
    val metadataKeys: Set<String> get() = format.metadataKeys

    fun copy(
        ref: PhotoRef = this.ref,
        bytes: ByteArray = this.bytes,
        width: Int = this.width,
        height: Int = this.height,
        format: PhotoFormat = this.format,
    ): RawPhoto = RawPhoto(ref, bytes, width, height, format)

    override fun equals(other: Any?): Boolean = other is RawPhoto &&
        ref == other.ref && bytes.contentEquals(other.bytes) && width == other.width && height == other.height &&
        format == other.format

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return 31 * result + format.hashCode()
    }
}

/** Trusted output produced by [PhotoInputNormalizer], with defensive byte access. */
class NormalizedPhotoInput(
    val ref: PhotoRef,
    bytes: ByteArray,
    val width: Int,
    val height: Int,
    val format: PhotoFormat,
    contentDigest: String,
) {
    private val storedBytes = bytes.copyOf()
    val bytes: ByteArray get() = storedBytes.copyOf()
    val contentDigest: String = contentDigest

    val orientation: ImageOrientation get() = format.orientation
    val colorSpace: String get() = format.colorSpace
    val metadataKeys: Set<String> get() = format.metadataKeys

    init {
        require(storedBytes.isNotEmpty()) { "Normalized photo bytes must not be empty" }
        require(width > 0 && height > 0) { "Normalized photo dimensions must be positive" }
        // One equality in place of three separate checks. The three messages are
        // kept apart because each names a distinct thing a platform adapter
        // failed to do, and that is what makes the failure actionable.
        require(format.orientation == ImageOrientation.UP) { "Normalized photo orientation must be UP" }
        require(format.colorSpace == "sRGB") { "Normalized photo color space must be sRGB" }
        require(format.metadataKeys.isEmpty()) { "Normalized photo metadata must be empty" }
        require(isSha256Digest(contentDigest)) { "Normalized photo digest must be SHA-256" }
    }

    /**
     * Format is deliberately not a parameter. It is invariant for this type —
     * anything other than [PhotoFormat.NORMALIZED] fails construction — so
     * offering it as something to vary would only advertise a copy that always
     * throws.
     */
    fun copy(
        ref: PhotoRef = this.ref,
        bytes: ByteArray = this.bytes,
        width: Int = this.width,
        height: Int = this.height,
        contentDigest: String = this.contentDigest,
    ): NormalizedPhotoInput = NormalizedPhotoInput(ref, bytes, width, height, format, contentDigest)

    internal fun wipeBytes() {
        storedBytes.fill(0)
    }

    override fun equals(other: Any?): Boolean = other is NormalizedPhotoInput &&
        ref == other.ref && bytes.contentEquals(other.bytes) && width == other.width && height == other.height &&
        format == other.format && contentDigest == other.contentDigest

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        return 31 * result + contentDigest.hashCode()
    }
}

class PhotoPreparationException(message: String) : IllegalArgumentException(message)

/**
 * Common deterministic preparation boundary. Platform adapters must decode,
 * orient, bound, and re-encode bytes before entering this layer.
 */
class PhotoInputNormalizer(
    private val maxBytes: Int = 10 * 1024 * 1024,
    private val maxDimension: Int = 4096,
    private val digest: (ByteArray) -> String,
) {
    init {
        require(maxBytes > 0) { "Photo byte bound must be positive" }
        require(maxDimension > 0) { "Photo dimension bound must be positive" }
    }

    fun normalize(raw: RawPhoto): NormalizedPhotoInput {
        prepareRequire(raw.bytes.isNotEmpty(), "Photo bytes must not be empty")
        prepareRequire(raw.bytes.size <= maxBytes, "Photo exceeds the size bound")
        prepareRequire(raw.width in 1..maxDimension, "Photo width is outside the bound")
        prepareRequire(raw.height in 1..maxDimension, "Photo height is outside the bound")
        prepareRequire(
            raw.orientation == ImageOrientation.UP,
            "Platform adapter must apply orientation before common normalization",
        )
        prepareRequire(
            raw.colorSpace == "sRGB",
            "Platform adapter must convert the photo to sRGB before common normalization",
        )
        prepareRequire(
            raw.metadataKeys.isEmpty(),
            "Platform adapter must strip metadata before common normalization",
        )

        val normalizedBytes = raw.bytes
        val normalizedDigest = digest(normalizedBytes)
        prepareRequire(isSha256Digest(normalizedDigest), "Platform digest must be SHA-256")
        return NormalizedPhotoInput(
            ref = raw.ref,
            bytes = normalizedBytes,
            width = raw.width,
            height = raw.height,
            format = PhotoFormat.NORMALIZED,
            contentDigest = normalizedDigest,
        )
    }

    private fun prepareRequire(condition: Boolean, message: String) {
        if (!condition) throw PhotoPreparationException(message)
    }
}

/** Port for attempt-scoped normalized photo content. */
interface PhotoSessionStorePort {
    fun put(input: NormalizedPhotoInput)
    fun read(ref: PhotoRef): NormalizedPhotoInput?
    fun remove(ref: PhotoRef): Boolean
    fun clearAttempt(attemptId: AttemptId): Int
}

/** Single-reducer in-memory content store with explicit secure cleanup. */
class PhotoSessionStore : PhotoSessionStorePort {
    private val contents = mutableMapOf<PhotoRef, NormalizedPhotoInput>()

    override fun put(input: NormalizedPhotoInput) {
        contents[input.ref] = input
    }

    override fun read(ref: PhotoRef): NormalizedPhotoInput? = contents[ref]

    override fun remove(ref: PhotoRef): Boolean {
        val removed = contents.remove(ref) ?: return false
        removed.wipeBytes()
        return true
    }

    /** Idempotent terminal cleanup that zeroes sensitive photo bytes before release. */
    override fun clearAttempt(attemptId: AttemptId): Int {
        val refs = contents.keys.filter { it.attemptId == attemptId }
        refs.forEach { ref -> contents.remove(ref)?.wipeBytes() }
        return refs.size
    }
}
