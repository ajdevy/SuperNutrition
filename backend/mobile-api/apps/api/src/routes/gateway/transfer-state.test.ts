import { describe, it, expect } from 'vitest';
import {
  TransferStateTracker,
  cancellationStatus,
  TERMINAL_RETENTION_SECONDS,
} from './transfer-state.js';

const NOW = new Date('2026-07-29T14:00:00Z');
const tracker = () => new TransferStateTracker(() => NOW);

describe('cancellation per transfer state (AD-6, AD-8)', () => {
  it('pre-transfer sends zero bytes and makes a complete guarantee', () => {
    const t = tracker();
    t.begin('att_1');
    const record = t.cancel('att_1');

    expect(record.transferState).toBe('pre-transfer');
    expect(record.bytesTransferred).toBe(0);
    expect(record.downstreamDeletionRequested).toBe(false);
    expect(record.resultSuppressed).toBe(false);
    expect(cancellationStatus(record)).toBe(204);
  });

  it('transferring aborts transport and discards partial bytes', () => {
    const t = tracker();
    t.begin('att_2');
    t.markTransferring('att_2', 4096);
    const record = t.cancel('att_2');

    expect(record.transferState).toBe('transferring');
    expect(record.bytesTransferred).toBe(4096);
    expect(record.downstreamDeletionRequested).toBe(false);
    expect(cancellationStatus(record)).toBe(202);
  });

  it('transferred suppresses the result and requests downstream deletion', () => {
    const t = tracker();
    t.begin('att_3');
    t.markTransferred('att_3', 8192);
    const record = t.cancel('att_3');

    expect(record.transferState).toBe('transferred');
    expect(record.downstreamDeletionRequested).toBe(true);
    expect(record.resultSuppressed).toBe(true);
    expect(cancellationStatus(record)).toBe(202);
  });

  // The single most important assertion in this file.
  it('NEVER claims recall, in any state', () => {
    const t = tracker();
    for (const [id, prepare] of [
      ['att_a', () => {}],
      ['att_b', () => t.markTransferring('att_b', 10)],
      ['att_c', () => t.markTransferred('att_c', 10)],
    ] as const) {
      t.begin(id);
      prepare();
      expect(t.cancel(id)?.recallClaimed, id).toBe(false);
    }
    // And after release, where the record is retained rather than invented.
    t.begin('att_d');
    t.markTransferred('att_d', 10);
    t.release('att_d');
    expect(t.cancel('att_d')?.recallClaimed).toBe(false);
  });

  it('aborts the attempt signal so downstream work stops', () => {
    const t = tracker();
    const controller = t.begin('att_4');
    expect(controller.signal.aborted).toBe(false);
    t.cancel('att_4');
    expect(controller.signal.aborted).toBe(true);
  });

  it('is idempotent: repeat cancellation returns the same terminal record', () => {
    const t = tracker();
    t.begin('att_5');
    t.markTransferred('att_5', 32);
    const first = t.cancel('att_5');
    const second = t.cancel('att_5');
    expect(second).toEqual(first);
  });

  it('makes no claim at all about an attempt it has no record of', () => {
    // It used to answer pre-transfer / 0 bytes / 204, whose contract meaning is
    // "Nothing was sent. This is a complete guarantee." The gateway cannot know
    // that about an attempt it cannot find, and the guarantee is about the
    // user's photo. Saying nothing is the only honest option.
    expect(tracker().cancel('att_never_seen')).toBeNull();
  });

  it('marks the attempt cancelled so a late result is discarded', () => {
    const t = tracker();
    t.begin('att_6');
    expect(t.isCancelled('att_6')).toBe(false);
    t.cancel('att_6');
    expect(t.isCancelled('att_6')).toBe(true);
  });

  it('releases state on every terminal path', () => {
    const t = tracker();
    t.begin('att_7');
    expect(t.activeCount).toBe(1);
    t.release('att_7');
    expect(t.activeCount).toBe(0);
  });

  it('ignores state transitions for an unknown attempt', () => {
    const t = tracker();
    t.markTransferring('ghost', 1);
    t.markTransferred('ghost', 1);
    expect(t.stateOf('ghost')).toBeNull();
  });
});

/**
 * H2 — cancelling an attempt that already finished.
 *
 * The normal client sequence is: send the photo, get a 200, then cancel to have
 * the bytes deleted downstream. By then the pipeline's `finally` has already run
 * `release()`, which deleted the attempt outright — so cancellation fell through
 * to the unknown-attempt branch and answered "pre-transfer, 0 bytes, 204",
 * whose contract meaning is a complete guarantee that nothing was sent. It had
 * been sent. To a provider.
 *
 * The fix retains the terminal record after release, bounded by a TTL, so the
 * gateway answers from what happened instead of from the absence of a map entry.
 */
describe('H2 — cancellation after the attempt has completed', () => {
  const clock = () => {
    let current = new Date('2026-07-29T14:00:00Z');
    return {
      now: () => current,
      advanceSeconds: (seconds: number) => {
        current = new Date(current.getTime() + seconds * 1000);
      },
    };
  };

  it('does not report that nothing was sent once bytes reached a provider', () => {
    const t = tracker();
    t.begin('att_done');
    t.markTransferred('att_done', 8192);
    t.release('att_done');

    const record = t.cancel('att_done');

    expect(record).not.toBeNull();
    expect(record?.transferState).toBe('transferred');
    expect(record?.bytesTransferred).toBe(8192);
    expect(record?.downstreamDeletionRequested).toBe(true);
    expect(cancellationStatus(record!)).toBe(202);
  });

  it('does not claim to suppress a result the client already has', () => {
    // resultSuppressed means "a result that is now discarded". A result already
    // returned in a 200 is not discarded by a later cancellation, and saying so
    // would be the same kind of false comfort as the 204.
    const t = tracker();
    t.begin('att_delivered');
    t.markTransferred('att_delivered', 64);
    t.markResultDelivered('att_delivered');
    t.release('att_delivered');

    expect(t.cancel('att_delivered')?.resultSuppressed).toBe(false);
  });

  it('still suppresses a result that never reached the client', () => {
    const t = tracker();
    t.begin('att_failed');
    t.markTransferred('att_failed', 64);
    t.release('att_failed'); // released by the error path: nothing was delivered

    expect(t.cancel('att_failed')?.resultSuppressed).toBe(true);
  });

  it('returns the same terminal record on repeat cancellation after release', () => {
    // transfer-states.toml: repeat_cancellation = "returns-the-same-terminal-record".
    const t = tracker();
    t.begin('att_twice');
    t.markTransferred('att_twice', 128);
    t.release('att_twice');

    const first = t.cancel('att_twice');
    const second = t.cancel('att_twice');
    expect(second).toEqual(first);
  });

  it('keeps a cancellation decided before release visible after it', () => {
    const t = tracker();
    t.begin('att_cancel_then_release');
    t.markTransferring('att_cancel_then_release', 512);
    const before = t.cancel('att_cancel_then_release');
    t.release('att_cancel_then_release');

    expect(t.cancel('att_cancel_then_release')).toEqual(before);
  });

  it('forgets retained records once the retention window passes', () => {
    // Unbounded retention would be a slow leak of one entry per attempt, and
    // the honest answer past the horizon is "no record", not a guarantee.
    const c = clock();
    const t = new TransferStateTracker(c.now);
    t.begin('att_expiring');
    t.markTransferred('att_expiring', 16);
    t.release('att_expiring');

    c.advanceSeconds(TERMINAL_RETENTION_SECONDS - 1);
    expect(t.cancel('att_expiring')).not.toBeNull();

    c.advanceSeconds(2);
    expect(t.cancel('att_expiring')).toBeNull();
  });

  it('does not count retained records as active transfers', () => {
    const t = tracker();
    t.begin('att_counted');
    expect(t.activeCount).toBe(1);
    t.release('att_counted');
    expect(t.activeCount).toBe(0);
    expect(t.retainedCount).toBe(1);
  });

  it('reports an attempt it never saw as not cancelled', () => {
    // `entry?.cancellation !== null` read `undefined !== null` for a missing
    // entry, so every unknown attempt answered "cancelled". The pipeline uses
    // this to decide whether to discard a fresh result.
    expect(tracker().isCancelled('att_never_seen')).toBe(false);
  });
});
