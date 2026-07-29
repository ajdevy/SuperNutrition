@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.isSha256Digest
import kotlin.concurrent.atomics.AtomicReference

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

/** Thread-safe in-memory lease store retained under the original public name. */
class PhotoLeaseStore : PhotoLeaseStorePort {
    private val active = AtomicReference<Set<PhotoRef>>(emptySet())

    override fun issue(attemptId: AttemptId, opaqueId: String): PhotoRef {
        val ref = PhotoRef.issue(attemptId, opaqueId)
        update { it + ref }
        return ref
    }

    override fun isActive(ref: PhotoRef): Boolean = ref in active.load()

    /** Returns false on repeated cleanup; repeated cleanup is still safe. */
    override fun revoke(ref: PhotoRef): Boolean = remove { it == ref }

    override fun revokeAttempt(attemptId: AttemptId): Int {
        while (true) {
            val current = active.load()
            val remaining = current.filterTo(mutableSetOf()) { it.attemptId != attemptId }
            val removed = current.size - remaining.size
            if (active.compareAndSet(current, remaining)) return removed
        }
    }

    private fun update(transform: (Set<PhotoRef>) -> Set<PhotoRef>) {
        while (true) {
            val current = active.load()
            if (active.compareAndSet(current, transform(current))) return
        }
    }

    private fun remove(predicate: (PhotoRef) -> Boolean): Boolean {
        while (true) {
            val current = active.load()
            val removed = current.firstOrNull(predicate) ?: return false
            if (active.compareAndSet(current, current - removed)) return true
        }
    }
}

enum class ImageOrientation {
    UP,
    RIGHT,
    DOWN,
    LEFT,
}

/** Byte values are copied at the boundary and on every read. */
class RawPhoto(
    val ref: PhotoRef,
    bytes: ByteArray,
    val width: Int,
    val height: Int,
    val orientation: ImageOrientation,
    val colorSpace: String,
    metadataKeys: Set<String>,
) {
    private val storedBytes = bytes.copyOf()
    val bytes: ByteArray get() = storedBytes.copyOf()
    val metadataKeys: Set<String> = metadataKeys.toSet()

    fun copy(
        ref: PhotoRef = this.ref,
        bytes: ByteArray = this.bytes,
        width: Int = this.width,
        height: Int = this.height,
        orientation: ImageOrientation = this.orientation,
        colorSpace: String = this.colorSpace,
        metadataKeys: Set<String> = this.metadataKeys,
    ): RawPhoto = RawPhoto(ref, bytes, width, height, orientation, colorSpace, metadataKeys)

    override fun equals(other: Any?): Boolean = other is RawPhoto &&
        ref == other.ref && bytes.contentEquals(other.bytes) && width == other.width && height == other.height &&
        orientation == other.orientation && colorSpace == other.colorSpace && metadataKeys == other.metadataKeys

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + orientation.hashCode()
        result = 31 * result + colorSpace.hashCode()
        return 31 * result + metadataKeys.hashCode()
    }
}

/** Trusted output produced by [PhotoInputNormalizer], with defensive byte access. */
class NormalizedPhotoInput(
    val ref: PhotoRef,
    bytes: ByteArray,
    val width: Int,
    val height: Int,
    val orientation: ImageOrientation,
    val colorSpace: String,
    metadataKeys: Set<String>,
    contentDigest: String,
) {
    private val storedBytes = bytes.copyOf()
    val bytes: ByteArray get() = storedBytes.copyOf()
    val metadataKeys: Set<String> = metadataKeys.toSet()
    val contentDigest: String = contentDigest

    fun copy(
        ref: PhotoRef = this.ref,
        bytes: ByteArray = this.bytes,
        width: Int = this.width,
        height: Int = this.height,
        orientation: ImageOrientation = this.orientation,
        colorSpace: String = this.colorSpace,
        metadataKeys: Set<String> = this.metadataKeys,
        contentDigest: String = this.contentDigest,
    ): NormalizedPhotoInput = NormalizedPhotoInput(
        ref,
        bytes,
        width,
        height,
        orientation,
        colorSpace,
        metadataKeys,
        contentDigest,
    )

    override fun equals(other: Any?): Boolean = other is NormalizedPhotoInput &&
        ref == other.ref && bytes.contentEquals(other.bytes) && width == other.width && height == other.height &&
        orientation == other.orientation && colorSpace == other.colorSpace && metadataKeys == other.metadataKeys &&
        contentDigest == other.contentDigest

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + orientation.hashCode()
        result = 31 * result + colorSpace.hashCode()
        result = 31 * result + metadataKeys.hashCode()
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
            orientation = ImageOrientation.UP,
            colorSpace = "sRGB",
            metadataKeys = emptySet(),
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

/** Thread-safe in-memory content store; removing content never mutates caller-owned bytes. */
class PhotoSessionStore : PhotoSessionStorePort {
    private val contents = AtomicReference<Map<PhotoRef, NormalizedPhotoInput>>(emptyMap())

    override fun put(input: NormalizedPhotoInput) {
        while (true) {
            val current = contents.load()
            if (contents.compareAndSet(current, current + (input.ref to input))) return
        }
    }

    override fun read(ref: PhotoRef): NormalizedPhotoInput? = contents.load()[ref]

    override fun remove(ref: PhotoRef): Boolean {
        while (true) {
            val current = contents.load()
            if (ref !in current) return false
            if (contents.compareAndSet(current, current - ref)) return true
        }
    }

    /** Idempotent terminal cleanup without mutating byte values. */
    override fun clearAttempt(attemptId: AttemptId): Int {
        while (true) {
            val current = contents.load()
            val remaining = current.filterKeys { it.attemptId != attemptId }
            val removed = current.size - remaining.size
            if (contents.compareAndSet(current, remaining)) return removed
        }
    }
}
