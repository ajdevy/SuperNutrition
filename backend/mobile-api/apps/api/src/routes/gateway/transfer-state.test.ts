import { describe, it, expect } from 'vitest';
import { TransferStateTracker, cancellationStatus } from './transfer-state.js';

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
      expect(t.cancel(id).recallClaimed, id).toBe(false);
    }
    // Also for an attempt this gateway never saw.
    expect(t.cancel('att_unknown').recallClaimed).toBe(false);
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

  it('treats an unknown attempt as pre-transfer', () => {
    const record = tracker().cancel('att_never_seen');
    expect(record.transferState).toBe('pre-transfer');
    expect(record.bytesTransferred).toBe(0);
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
