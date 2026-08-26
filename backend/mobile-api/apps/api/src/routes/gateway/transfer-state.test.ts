import { describe, it, expect } from 'vitest';
import {
  TransferStateTracker,
  cancellationStatus,
  TERMINAL_RETENTION_SECONDS,
  type CancellationRecord,
} from './transfer-state.js';

const NOW = new Date('2026-07-29T14:00:00Z');
const tracker = () => new TransferStateTracker(() => NOW);

/**
 * `cancel()` returns null for an attempt the tracker does not hold, and the
 * tests that use the record all begin by claiming one. Asserted rather than
 * silenced with `!`: if a claim ever stops taking, the failure names that
 * instead of a property access on null.
 */
/**
 * `begin()` returns null when the id is already held, and `cancel()` returns
 * null for an attempt the tracker does not hold. The tests below claim a fresh
 * one first, so null means the setup broke — asserted rather than silenced with
 * `!`, so the failure names that instead of a property access on null.
 */
const claimed = (controller: AbortController | null): AbortController => {
  if (controller === null) throw new Error('expected a fresh attempt claim, got null');
  return controller;
};

const cancelled = (record: CancellationRecord | null): CancellationRecord => {
  if (record === null) throw new Error('expected a cancellation record, got null');
  return record;
};

describe('cancellation per transfer state (AD-6, AD-8)', () => {
  it('pre-transfer sends zero bytes and makes a complete guarantee', () => {
    const t = tracker();
    t.begin('att_1', 'installation-a');
    const record = cancelled(t.cancel('att_1', 'installation-a'));

    expect(record.transferState).toBe('pre-transfer');
    expect(record.bytesTransferred).toBe(0);
    expect(record.downstreamDeletionRequested).toBe(false);
    expect(record.resultSuppressed).toBe(false);
    expect(cancellationStatus(record)).toBe(204);
  });

  it('transferring aborts transport and discards partial bytes', () => {
    const t = tracker();
    t.begin('att_2', 'installation-a');
    t.markTransferring('att_2', 4096);
    const record = cancelled(t.cancel('att_2', 'installation-a'));

    expect(record.transferState).toBe('transferring');
    expect(record.bytesTransferred).toBe(4096);
    expect(record.downstreamDeletionRequested).toBe(false);
    expect(cancellationStatus(record)).toBe(202);
  });

  it('transferred suppresses the result, and claims no deletion nobody asked for', () => {
    const t = tracker();
    t.begin('att_3', 'installation-a');
    t.markTransferred('att_3', 8192);
    const record = cancelled(t.cancel('att_3', 'installation-a'));

    expect(record.transferState).toBe('transferred');
    // H-02 — a transfer is not a deletion request. No adapter exposes an
    // erasure operation, so there is nothing to have asked for.
    expect(record.downstreamDeletionRequested).toBe(false);
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
      t.begin(id, 'installation-a');
      prepare();
      expect(t.cancel(id, 'installation-a')?.recallClaimed, id).toBe(false);
    }
    // And after release, where the record is retained rather than invented.
    t.begin('att_d', 'installation-a');
    t.markTransferred('att_d', 10);
    t.release('att_d');
    expect(t.cancel('att_d', 'installation-a')?.recallClaimed).toBe(false);
  });

  it('aborts the attempt signal so downstream work stops', () => {
    const t = tracker();
    const controller = claimed(t.begin('att_4', 'installation-a'));
    expect(controller.signal.aborted).toBe(false);
    t.cancel('att_4', 'installation-a');
    expect(controller.signal.aborted).toBe(true);
  });

  it('is idempotent: repeat cancellation returns the same terminal record', () => {
    const t = tracker();
    t.begin('att_5', 'installation-a');
    t.markTransferred('att_5', 32);
    const first = t.cancel('att_5', 'installation-a');
    const second = t.cancel('att_5', 'installation-a');
    expect(second).toEqual(first);
  });

  it('makes no claim at all about an attempt it has no record of', () => {
    // It used to answer pre-transfer / 0 bytes / 204, whose contract meaning is
    // "Nothing was sent. This is a complete guarantee." The gateway cannot know
    // that about an attempt it cannot find, and the guarantee is about the
    // user's photo. Saying nothing is the only honest option.
    expect(tracker().cancel('att_never_seen', 'installation-a')).toBeNull();
  });

  it('marks the attempt cancelled so a late result is discarded', () => {
    const t = tracker();
    t.begin('att_6', 'installation-a');
    expect(t.isCancelled('att_6')).toBe(false);
    t.cancel('att_6', 'installation-a');
    expect(t.isCancelled('att_6')).toBe(true);
  });

  it('releases state on every terminal path', () => {
    const t = tracker();
    t.begin('att_7', 'installation-a');
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
    t.begin('att_done', 'installation-a');
    t.markTransferred('att_done', 8192);
    t.release('att_done');

    const record = cancelled(t.cancel('att_done', 'installation-a'));

    expect(record).not.toBeNull();
    expect(record?.transferState).toBe('transferred');
    expect(record?.bytesTransferred).toBe(8192);
    // H-02 — bytes reached a provider; nothing asked for them back.
    expect(record?.downstreamDeletionRequested).toBe(false);
    expect(cancellationStatus(record!)).toBe(202);
  });

  it('does not claim to suppress a result the client already has', () => {
    // resultSuppressed means "a result that is now discarded". A result already
    // returned in a 200 is not discarded by a later cancellation, and saying so
    // would be the same kind of false comfort as the 204.
    const t = tracker();
    t.begin('att_delivered', 'installation-a');
    t.markTransferred('att_delivered', 64);
    t.markResultDelivered('att_delivered');
    t.release('att_delivered');

    expect(t.cancel('att_delivered', 'installation-a')?.resultSuppressed).toBe(false);
  });

  it('still suppresses a result that never reached the client', () => {
    const t = tracker();
    t.begin('att_failed', 'installation-a');
    t.markTransferred('att_failed', 64);
    t.release('att_failed'); // released by the error path: nothing was delivered

    expect(t.cancel('att_failed', 'installation-a')?.resultSuppressed).toBe(true);
  });

  it('returns the same terminal record on repeat cancellation after release', () => {
    // transfer-states.toml: repeat_cancellation = "returns-the-same-terminal-record".
    const t = tracker();
    t.begin('att_twice', 'installation-a');
    t.markTransferred('att_twice', 128);
    t.release('att_twice');

    const first = t.cancel('att_twice', 'installation-a');
    const second = t.cancel('att_twice', 'installation-a');
    expect(second).toEqual(first);
  });

  it('keeps a cancellation decided before release visible after it', () => {
    const t = tracker();
    t.begin('att_cancel_then_release', 'installation-a');
    t.markTransferring('att_cancel_then_release', 512);
    const before = t.cancel('att_cancel_then_release', 'installation-a');
    t.release('att_cancel_then_release');

    expect(t.cancel('att_cancel_then_release', 'installation-a')).toEqual(before);
  });

  it('forgets retained records once the retention window passes', () => {
    // Unbounded retention would be a slow leak of one entry per attempt, and
    // the honest answer past the horizon is "no record", not a guarantee.
    const c = clock();
    const t = new TransferStateTracker(c.now);
    t.begin('att_expiring', 'installation-a');
    t.markTransferred('att_expiring', 16);
    t.release('att_expiring');

    c.advanceSeconds(TERMINAL_RETENTION_SECONDS - 1);
    expect(t.cancel('att_expiring', 'installation-a')).not.toBeNull();

    c.advanceSeconds(2);
    expect(t.cancel('att_expiring', 'installation-a')).toBeNull();
  });

  it('does not count retained records as active transfers', () => {
    const t = tracker();
    t.begin('att_counted', 'installation-a');
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

describe('M2 — a cancellation may only come from the installation that began it', () => {
  it('refuses a cancellation from a different installation', () => {
    // The DELETE route authenticated the caller and then threw the result away.
    // Attempt ids are chosen by the client, so any authenticated installation
    // could cancel — and read the transfer record of — anyone else's attempt.
    const t = tracker();
    t.begin('att_owned', 'installation-a');
    t.markTransferred('att_owned', 4096);

    expect(t.cancel('att_owned', 'installation-b')).toBeNull();
    // And the real owner is unaffected by the attempt.
    expect(t.cancel('att_owned', 'installation-a')?.transferState).toBe('transferred');
  });

  it('answers a foreign cancellation exactly as it answers an unknown one', () => {
    // Both are null, so a caller cannot use the response to learn whether an
    // attempt id exists. A distinguishable "forbidden" would be an oracle.
    const t = tracker();
    t.begin('att_secret', 'installation-a');
    expect(t.cancel('att_secret', 'installation-b')).toBe(t.cancel('att_absent', 'installation-b'));
  });

  it('still refuses after the attempt has been released', () => {
    const t = tracker();
    t.begin('att_retained', 'installation-a');
    t.markTransferred('att_retained', 8);
    t.release('att_retained');

    expect(t.cancel('att_retained', 'installation-b')).toBeNull();
    expect(t.cancel('att_retained', 'installation-a')).not.toBeNull();
  });
});

describe('M7 — one transfer per attempt id', () => {
  it('refuses to begin an attempt id that is already running', () => {
    // effects.claimOnce keys on effectId, so the same attemptId with a fresh
    // effectId walked straight past at-most-once. begin() then overwrote the
    // live entry, orphaning the first request's AbortController — nothing could
    // cancel it any more.
    const t = tracker();
    expect(t.begin('att_once', 'installation-a')).not.toBeNull();
    expect(t.begin('att_once', 'installation-a')).toBeNull();
  });

  it('refuses to reuse an attempt id that already finished', () => {
    // Retention is what makes this checkable: for as long as the gateway can
    // describe an attempt, it can also refuse to run it twice.
    const t = tracker();
    t.begin('att_reused', 'installation-a');
    t.markTransferred('att_reused', 16);
    t.release('att_reused');

    expect(t.begin('att_reused', 'installation-a')).toBeNull();
  });

  it('does not let a second installation seize a running attempt id', () => {
    const t = tracker();
    const first = t.begin('att_contested', 'installation-a');
    expect(t.begin('att_contested', 'installation-b')).toBeNull();
    // The original controller still governs the running transfer.
    expect(first?.signal.aborted).toBe(false);
    t.cancel('att_contested', 'installation-a');
    expect(first?.signal.aborted).toBe(true);
  });
});

describe('H-02 — the deletion flag reports a request that was actually made', () => {
  // downstreamDeletionRequested was `transferred && providerCalled`: true the
  // moment a provider had been called. The contract says it is true only when a
  // provider received the payload AND a deletion request was issued, and the
  // provider port exposes inference only — there is no deletion operation to
  // invoke. So the field asserted an erasure nobody had asked for, in the one
  // response a user reads to find out what happened to their photo.
  //
  // This does not change when a transfer is considered complete; the state
  // machine is untouched. Only the claim is corrected.
  const tracker = () => new TransferStateTracker(() => new Date('2026-07-29T10:20:00Z'));

  it('is false after a provider call, because nothing was asked to delete', () => {
    const t = tracker();
    t.begin('att_0000000000000001', 'inst-1');
    t.markTransferred('att_0000000000000001', 1024);

    const record = cancelled(t.cancel('att_0000000000000001', 'inst-1'));

    expect(record?.downstreamDeletionRequested).toBe(false);
  });

  it('is false before any transfer', () => {
    const t = tracker();
    t.begin('att_0000000000000002', 'inst-1');

    expect(t.cancel('att_0000000000000002', 'inst-1')?.downstreamDeletionRequested).toBe(false);
  });

  it('becomes true only once a deletion is actually recorded', () => {
    const t = tracker();
    t.begin('att_0000000000000003', 'inst-1');
    t.markTransferred('att_0000000000000003', 1024);
    t.markDownstreamDeletionRequested('att_0000000000000003');

    expect(t.cancel('att_0000000000000003', 'inst-1')?.downstreamDeletionRequested).toBe(true);
  });

  it('never claims recall, whatever else happened', () => {
    const t = tracker();
    t.begin('att_0000000000000004', 'inst-1');
    t.markTransferred('att_0000000000000004', 1024);
    t.markDownstreamDeletionRequested('att_0000000000000004');

    expect(t.cancel('att_0000000000000004', 'inst-1')?.recallClaimed).toBe(false);
  });
});

/**
 * #48(a)/(b) — the four cancellation scenarios handover-4 named.
 *
 * The state machine used to reach terminal `transferred` the moment the gateway
 * handed the payload on, before any provider had admitted it. So an attempt the
 * provider refused, timed out on, or never saw was recorded as transferred, and
 * every downstream decision — retry, cancellation, suppression — reasoned from a
 * completion that had not happened.
 *
 * `transferred` now requires provider admission, which is what the release says
 * (`provider_called = true`). The deletion flag stays false until an adapter can
 * actually request erasure, and becomes true only after exactly one such call.
 */
describe('#48 — cancellation across the real transfer lifecycle', () => {
  const OWNER = 'inst_1';
  const ATTEMPT = 'att_0000000000000048';

  const tracker = () => new TransferStateTracker(() => new Date('2026-08-15T10:00:00Z'));

  it('cancelling before any provider POST reports pre-transfer and no deletion', () => {
    const t = tracker();
    t.begin(ATTEMPT, OWNER);

    const record = cancelled(t.cancel(ATTEMPT, OWNER));

    expect(record?.transferState).toBe('pre-transfer');
    expect(record?.bytesTransferred).toBe(0);
    expect(record?.downstreamDeletionRequested).toBe(false);
    expect(record?.resultSuppressed).toBe(false);
  });

  it('cancelling during an aborting POST reports transferring, not transferred', () => {
    // The inflight window: bytes are on the wire, the provider has not admitted
    // them. Reporting `transferred` here claimed an admission that had not
    // happened; reporting `pre-transfer` would claim no provider saw anything,
    // which is equally unbackable.
    const t = tracker();
    t.begin(ATTEMPT, OWNER);
    t.markTransferring(ATTEMPT, 2048);

    const record = cancelled(t.cancel(ATTEMPT, OWNER));

    expect(record?.transferState).toBe('transferring');
    expect(record?.bytesTransferred).toBe(2048);
    expect(record?.downstreamDeletionRequested).toBe(false);
  });

  it('cancelling after provider admission with no delete support reports transferred and false', () => {
    const t = tracker();
    t.begin(ATTEMPT, OWNER);
    t.markTransferring(ATTEMPT, 2048);
    t.markTransferred(ATTEMPT, 2048);

    const record = cancelled(t.cancel(ATTEMPT, OWNER));

    expect(record?.transferState).toBe('transferred');
    expect(record?.bytesTransferred).toBe(2048);
    expect(record?.resultSuppressed).toBe(true);
    // No adapter exposes erasure, so nothing was requested of anyone.
    expect(record?.downstreamDeletionRequested).toBe(false);
    expect(record?.recallClaimed).toBe(false);
  });

  it('reports true only after exactly one real deletion invocation', () => {
    const t = tracker();
    t.begin(ATTEMPT, OWNER);
    t.markTransferring(ATTEMPT, 2048);
    t.markTransferred(ATTEMPT, 2048);
    t.markDownstreamDeletionRequested(ATTEMPT);

    const record = cancelled(t.cancel(ATTEMPT, OWNER));

    expect(record?.downstreamDeletionRequested).toBe(true);
    // Still no recall claim: a deletion REQUEST is not a guarantee the bytes
    // were destroyed, and the release is explicit that the gateway cannot make
    // that true (AD-8).
    expect(record?.recallClaimed).toBe(false);
  });

  it('does not report deletion for an attempt that never reached the provider', () => {
    // Guard against the flag being set on a state where it cannot be true.
    const t = tracker();
    t.begin(ATTEMPT, OWNER);
    t.markTransferring(ATTEMPT, 2048);
    t.markDownstreamDeletionRequested(ATTEMPT);

    const record = cancelled(t.cancel(ATTEMPT, OWNER));

    expect(record?.transferState).toBe('transferring');
    expect(record?.downstreamDeletionRequested).toBe(false);
  });
});
