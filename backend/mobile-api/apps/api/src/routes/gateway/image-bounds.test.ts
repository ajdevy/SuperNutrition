import { describe, it, expect } from 'vitest';
import { imageDimensions, magicBytesMatch, withinDimensionBound } from './image-bounds.js';

/**
 * H-04 — the payload was accepted on a four-byte prefix and never measured.
 *
 * `image/webp` was matched on `RIFF` alone, which is the container magic for
 * WAVE and AVI too, so an audio file declared as WebP reached the provider. And
 * nothing read the dimensions at all, while
 * `contracts/mobile-api/v1/gateway-request.toml` pins
 * `payload_max_dimension_px = 4096` and lists `dimension-bounds` in the
 * validation order, ahead of authentication and any provider call.
 */

const be16 = (n: number) => [(n >> 8) & 0xff, n & 0xff];
const be32 = (n: number) => [(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff];
const ascii = (s: string) => [...s].map((c) => c.charCodeAt(0));

const png = (w: number, h: number) =>
  Uint8Array.from([
    0x89,
    0x50,
    0x4e,
    0x47,
    0x0d,
    0x0a,
    0x1a,
    0x0a,
    ...be32(13),
    ...ascii('IHDR'),
    ...be32(w),
    ...be32(h),
    8,
    2,
    0,
    0,
    0,
  ]);

/** Baseline JPEG: SOI, APP0, then an SOF0 carrying the real size. */
const jpeg = (w: number, h: number) =>
  Uint8Array.from([
    0xff,
    0xd8,
    0xff,
    0xe0,
    ...be16(16),
    ...ascii('JFIF'),
    0,
    1,
    1,
    0,
    0,
    1,
    0,
    1,
    0,
    0,
    0xff,
    0xc0,
    ...be16(17),
    8,
    ...be16(h),
    ...be16(w),
    3,
    1,
    0x22,
    0,
    2,
    0x11,
    1,
    3,
    0x11,
    1,
  ]);

/** Lossy WebP: RIFF/WEBP/VP8 with a 14-bit width and height. */
const webp = (w: number, h: number) =>
  Uint8Array.from([
    ...ascii('RIFF'),
    // RIFF size counts everything after this 8-byte header: 22 of 30 bytes.
    22,
    0,
    0,
    0,
    ...ascii('WEBP'),
    ...ascii('VP8 '),
    // Chunk size: frame tag (3) + start code (3) + width (2) + height (2).
    10,
    0,
    0,
    0,
    // 3-byte frame tag, then the start code: the size sits at 26..29.
    0x00,
    0x00,
    0x00,
    0x9d,
    0x01,
    0x2a,
    w & 0xff,
    (w >> 8) & 0x3f,
    h & 0xff,
    (h >> 8) & 0x3f,
  ]);

/** A RIFF container that is not an image at all. */
const wave = Uint8Array.from([...ascii('RIFF'), 0, 0, 0, 0, ...ascii('WAVEfmt ')]);

describe('the declared content type must match the bytes', () => {
  it('accepts each format the release permits', () => {
    expect(magicBytesMatch('image/jpeg', jpeg(10, 10))).toBe(true);
    expect(magicBytesMatch('image/png', png(10, 10))).toBe(true);
    expect(magicBytesMatch('image/webp', webp(10, 10))).toBe(true);
  });

  it('refuses a RIFF container that is not WebP', () => {
    // The whole point: RIFF alone is WAVE, AVI and WebP. Matching on it let an
    // audio file through as an image.
    expect(magicBytesMatch('image/webp', wave)).toBe(false);
  });

  it('refuses a truncated header rather than reading past it', () => {
    expect(magicBytesMatch('image/webp', Uint8Array.from(ascii('RIFF')))).toBe(false);
    expect(magicBytesMatch('image/png', Uint8Array.from([0x89, 0x50]))).toBe(false);
  });

  it('refuses a content type the release does not list', () => {
    expect(magicBytesMatch('image/gif', png(10, 10))).toBe(false);
  });
});

describe('dimensions are read from the bytes, not trusted', () => {
  it.each([
    ['jpeg', jpeg(4096, 2160)],
    ['png', png(4096, 2160)],
    ['webp', webp(4096, 2160)],
  ])('reads %s width and height', (_f, bytes) => {
    expect(imageDimensions(bytes)).toEqual({ width: 4096, height: 2160 });
  });

  it('reads a jpeg whose size sits behind several segments', () => {
    const padded = Uint8Array.from([
      0xff,
      0xd8,
      0xff,
      0xe1,
      ...be16(6),
      1,
      2,
      3,
      4,
      0xff,
      0xdb,
      ...be16(4),
      9,
      9,
      0xff,
      0xc2,
      ...be16(17),
      8,
      ...be16(120),
      ...be16(340),
      3,
      1,
      0x22,
      0,
      2,
      0x11,
      1,
      3,
      0x11,
      1,
    ]);
    expect(imageDimensions(padded)).toEqual({ width: 340, height: 120 });
  });

  it('returns null when the header is truncated or unreadable', () => {
    expect(imageDimensions(Uint8Array.from([0xff, 0xd8, 0xff]))).toBeNull();
    expect(imageDimensions(Uint8Array.from(ascii('RIFF')))).toBeNull();
    expect(imageDimensions(wave)).toBeNull();
    expect(imageDimensions(Uint8Array.from([]))).toBeNull();
  });

  it('does not loop forever on a jpeg with a zero-length segment', () => {
    const hostile = Uint8Array.from([0xff, 0xd8, 0xff, 0xe1, 0, 0, 0xff, 0xe1, 0, 0]);
    expect(imageDimensions(hostile)).toBeNull();
  });
});

/**
 * P-03 — the first pass read bytes at offsets without checking the structures
 * that make those offsets meaningful.
 *
 * PNG dimensions were read from offsets 16..23 on the strength of the 8-byte
 * signature alone, so any file starting with that signature had "dimensions".
 * The JPEG walker accepted an SOF marker without checking its segment was long
 * enough to contain the size it was about to read, and refused legal marker-fill
 * bytes (0xFF padding before a marker), which valid encoders emit. The WebP path
 * trusted the format FourCC without validating the RIFF or chunk lengths, or the
 * VP8/VP8L start codes.
 *
 * The consequence runs both ways: forged headers reached a provider, and
 * legitimate photographs were rejected.
 */
describe('P-03 — headers are parsed as structures, not read at offsets', () => {
  it('refuses a PNG signature whose first chunk is not IHDR', () => {
    // sIGnature + a valid-looking chunk that simply is not IHDR. The old reader
    // returned whatever happened to sit at offsets 16..23.
    const forged = Uint8Array.from([
      0x89,
      0x50,
      0x4e,
      0x47,
      0x0d,
      0x0a,
      0x1a,
      0x0a,
      ...be32(13),
      ...ascii('gAMA'),
      ...be32(64),
      ...be32(64),
      8,
      2,
      0,
      0,
      0,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('refuses an IHDR whose declared length is not 13', () => {
    const forged = Uint8Array.from([
      0x89,
      0x50,
      0x4e,
      0x47,
      0x0d,
      0x0a,
      0x1a,
      0x0a,
      ...be32(9),
      ...ascii('IHDR'),
      ...be32(64),
      ...be32(64),
      8,
      2,
      0,
      0,
      0,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('refuses a JPEG SOF whose segment is too short to hold the dimensions', () => {
    // Length 5 cannot contain precision + height + width. The old walker read
    // past it anyway.
    const forged = Uint8Array.from([
      0xff,
      0xd8,
      0xff,
      0xc0,
      ...be16(5),
      8,
      ...be16(64),
      ...be16(64),
      3,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('reads a JPEG that uses legal marker-fill bytes', () => {
    // 0xFF may repeat as fill before a marker. Refusing it rejected valid files.
    const filled = Uint8Array.from([
      0xff,
      0xd8,
      0xff,
      0xe0,
      ...be16(4),
      1,
      2,
      0xff,
      0xff,
      0xff,
      0xc0,
      ...be16(17),
      8,
      ...be16(240),
      ...be16(320),
      3,
      1,
      0x22,
      0,
      2,
      0x11,
      1,
      3,
      0x11,
      1,
    ]);
    expect(imageDimensions(filled)).toEqual({ width: 320, height: 240 });
  });

  it('refuses a WebP whose RIFF length does not cover the file', () => {
    const forged = Uint8Array.from([
      ...ascii('RIFF'),
      0xff,
      0xff,
      0xff,
      0x7f,
      ...ascii('WEBP'),
      ...ascii('VP8 '),
      10,
      0,
      0,
      0,
      0x00,
      0x00,
      0x00,
      0x9d,
      0x01,
      0x2a,
      64,
      0,
      64,
      0,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('refuses a lossy WebP without the VP8 start code', () => {
    const forged = Uint8Array.from([
      ...ascii('RIFF'),
      24,
      0,
      0,
      0,
      ...ascii('WEBP'),
      ...ascii('VP8 '),
      12,
      0,
      0,
      0,
      0x00,
      0x00,
      0x00,
      0xde,
      0xad,
      0xbe,
      64,
      0,
      64,
      0,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('refuses a lossless WebP without the VP8L signature byte', () => {
    const forged = Uint8Array.from([
      ...ascii('RIFF'),
      24,
      0,
      0,
      0,
      ...ascii('WEBP'),
      ...ascii('VP8L'),
      9,
      0,
      0,
      0,
      0x00,
      0x3f,
      0x00,
      0xf0,
      0x3f,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('refuses a WebP whose chunk length runs past the buffer', () => {
    const forged = Uint8Array.from([
      ...ascii('RIFF'),
      24,
      0,
      0,
      0,
      ...ascii('WEBP'),
      ...ascii('VP8 '),
      0xff,
      0xff,
      0,
      0,
      0x00,
      0x00,
      0x00,
      0x9d,
      0x01,
      0x2a,
      64,
      0,
      64,
      0,
    ]);
    expect(imageDimensions(forged)).toBeNull();
  });

  it('still accepts each format at exactly the released bound', () => {
    expect(withinDimensionBound(jpeg(4096, 4096))).toBe(true);
    expect(withinDimensionBound(png(4096, 4096))).toBe(true);
    expect(withinDimensionBound(webp(4096, 4096))).toBe(true);
  });

  it('still refuses each format one pixel over it', () => {
    expect(withinDimensionBound(jpeg(4097, 10))).toBe(false);
    expect(withinDimensionBound(png(10, 4097))).toBe(false);
    // 4097 does not fit WebP's 14-bit field, so PNG and JPEG carry this case.
  });
});
