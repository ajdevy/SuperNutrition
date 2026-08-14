import { describe, it, expect } from 'vitest';
import { imageDimensions, magicBytesMatch } from './image-bounds.js';

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
    0,
    0,
    0,
    0,
    ...ascii('WEBP'),
    ...ascii('VP8 '),
    0,
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
