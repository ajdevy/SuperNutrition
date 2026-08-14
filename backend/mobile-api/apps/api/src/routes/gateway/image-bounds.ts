/**
 * Format and dimension checks on the payload bytes themselves.
 *
 * `contracts/mobile-api/v1/gateway-request.toml` lists `magic-byte-sniff` and
 * `dimension-bounds` in its validation order, both ahead of authentication and
 * far ahead of any provider call, and pins `payload_max_dimension_px = 4096`.
 *
 * H-04 — the previous check read four bytes and stopped. `image/webp` matched on
 * `RIFF`, which is equally the container magic for WAVE and AVI, so an audio
 * file declared as WebP was forwarded to a provider. Nothing measured the image
 * at all, so the released dimension bound was documentation.
 *
 * Everything here reads a header prefix only. No pixels are decoded: the point
 * is to reject cheaply, before the expensive and privacy-relevant act.
 */

/** Longest dimension the release admits. */
export const MAX_DIMENSION_PX = 4096;

const JPEG_SOI = [0xff, 0xd8, 0xff];
const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
const RIFF = [0x52, 0x49, 0x46, 0x46];
/** 'WEBP', at bytes 8..11 — the part that distinguishes WebP from WAVE. */
const WEBP_FOURCC = [0x57, 0x45, 0x42, 0x50];

const startsWith = (bytes: Uint8Array, signature: readonly number[], offset = 0): boolean =>
  bytes.length >= offset + signature.length &&
  signature.every((byte, index) => bytes[offset + index] === byte);

export const isJpeg = (bytes: Uint8Array): boolean => startsWith(bytes, JPEG_SOI);
export const isPng = (bytes: Uint8Array): boolean => startsWith(bytes, PNG_SIGNATURE);

/** RIFF *and* the WEBP FourCC. Either alone is not a WebP. */
export const isWebp = (bytes: Uint8Array): boolean =>
  startsWith(bytes, RIFF) && startsWith(bytes, WEBP_FOURCC, 8);

const MATCHERS: Readonly<Record<string, (bytes: Uint8Array) => boolean>> = {
  'image/jpeg': isJpeg,
  'image/png': isPng,
  'image/webp': isWebp,
};

/** True when the bytes really are the content type the caller declared. */
export const magicBytesMatch = (contentType: string, bytes: Uint8Array): boolean =>
  MATCHERS[contentType]?.(bytes) ?? false;

export interface ImageDimensions {
  readonly width: number;
  readonly height: number;
}

const be16 = (bytes: Uint8Array, at: number): number => (bytes[at]! << 8) | bytes[at + 1]!;
const be32 = (bytes: Uint8Array, at: number): number =>
  ((bytes[at]! << 24) | (bytes[at + 1]! << 16) | (bytes[at + 2]! << 8) | bytes[at + 3]!) >>> 0;

/** PNG puts IHDR first, so width and height sit at fixed offsets. */
const pngDimensions = (bytes: Uint8Array): ImageDimensions | null => {
  if (bytes.length < 24) return null;
  return { width: be32(bytes, 16), height: be32(bytes, 20) };
};

/**
 * JPEG carries its size in a start-of-frame marker, which sits behind a
 * variable run of other segments. Walk them rather than guessing an offset.
 */
const jpegDimensions = (bytes: Uint8Array): ImageDimensions | null => {
  let at = 2;
  while (at + 9 < bytes.length) {
    if (bytes[at] !== 0xff) return null;
    const marker = bytes[at + 1]!;
    // Standalone markers carry no length payload.
    if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
      at += 2;
      continue;
    }
    const length = be16(bytes, at + 2);
    // A segment shorter than its own length field would let `at` stand still.
    if (length < 2) return null;
    const isStartOfFrame =
      marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isStartOfFrame) {
      if (at + 9 >= bytes.length) return null;
      return { height: be16(bytes, at + 5), width: be16(bytes, at + 7) };
    }
    at += 2 + length;
  }
  return null;
};

/**
 * WebP has three body formats and each stores the canvas differently.
 *
 * VP8 is lossy and packs 14-bit dimensions after a start code; VP8L is lossless
 * and packs 14-bit dimensions into a bit field; VP8X is the extended header and
 * stores each dimension minus one as a 24-bit little-endian value.
 */
const webpDimensions = (bytes: Uint8Array): ImageDimensions | null => {
  if (!isWebp(bytes) || bytes.length < 16) return null;
  const format = String.fromCharCode(bytes[12]!, bytes[13]!, bytes[14]!, bytes[15]!);

  if (format === 'VP8 ') {
    if (bytes.length < 30) return null;
    return {
      width: (bytes[26]! | (bytes[27]! << 8)) & 0x3fff,
      height: (bytes[28]! | (bytes[29]! << 8)) & 0x3fff,
    };
  }

  if (format === 'VP8L') {
    if (bytes.length < 25) return null;
    const bits = bytes[21]! | (bytes[22]! << 8) | (bytes[23]! << 16) | (bytes[24]! << 24);
    return { width: (bits & 0x3fff) + 1, height: ((bits >> 14) & 0x3fff) + 1 };
  }

  if (format === 'VP8X') {
    if (bytes.length < 30) return null;
    return {
      width: (bytes[24]! | (bytes[25]! << 8) | (bytes[26]! << 16)) + 1,
      height: (bytes[27]! | (bytes[28]! << 8) | (bytes[29]! << 16)) + 1,
    };
  }

  return null;
};

/**
 * Width and height read from the header, or null when the bytes do not carry a
 * readable one. Null is a rejection, never an assumption that the image is fine.
 */
export const imageDimensions = (bytes: Uint8Array): ImageDimensions | null => {
  const read = isPng(bytes)
    ? pngDimensions(bytes)
    : isJpeg(bytes)
      ? jpegDimensions(bytes)
      : isWebp(bytes)
        ? webpDimensions(bytes)
        : null;
  if (read === null) return null;
  // A zero dimension is not a picture, and both are read from attacker-supplied
  // bytes, so neither is trusted to be sane.
  if (!Number.isInteger(read.width) || !Number.isInteger(read.height)) return null;
  if (read.width <= 0 || read.height <= 0) return null;
  return read;
};

/** True when the header is readable and both sides are within the release bound. */
export const withinDimensionBound = (bytes: Uint8Array): boolean => {
  const size = imageDimensions(bytes);
  return size !== null && size.width <= MAX_DIMENSION_PX && size.height <= MAX_DIMENSION_PX;
};
