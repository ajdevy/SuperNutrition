import type { TransferState } from '@supernutrition/shared';

/**
 * Per-attempt transfer state and cancellation (AD-6, contract
 * transfer-states.toml).
 *
 * What the gateway can honestly promise changes once bytes have left, and this
 * tracker is what makes that difference explicit rather than implied.
 */

export interface CancellationRecord {
  readonly attemptId: string;
  readonly transferState: TransferState;
  readonly bytesTransferred: number;
  /** ALWAYS false. Reporting a deletion request as a guarantee is a claim the gateway cannot keep. */
  readonly recallClaimed: false;
  readonly downstreamDeletionRequested: boolean;
  readonly resultSuppressed: boolean;
  readonly cancelledAt: string;
}

interface AttemptEntry {
  state: TransferState;
  bytes: number;
  providerCalled: boolean;
  controller: AbortController;
  cancellation: CancellationRecord | null;
}

export class TransferStateTracker {
  readonly #attempts = new Map<string, AttemptEntry>();
  readonly #now: () => Date;

  constructor(now: () => Date = () => new Date()) {
    this.#now = now;
  }

  begin(attemptId: string): AbortController {
    const controller = new AbortController();
    this.#attempts.set(attemptId, {
      state: 'pre-transfer',
      bytes: 0,
      providerCalled: false,
      controller,
      cancellation: null,
    });
    return controller;
  }

  markTransferring(attemptId: string, bytes: number): void {
    const entry = this.#attempts.get(attemptId);
    if (!entry) return;
    entry.state = 'transferring';
    entry.bytes = bytes;
  }

  markTransferred(attemptId: string, bytes: number): void {
    const entry = this.#attempts.get(attemptId);
    if (!entry) return;
    entry.state = 'transferred';
    entry.bytes = bytes;
    entry.providerCalled = true;
  }

  stateOf(attemptId: string): TransferState | null {
    return this.#attempts.get(attemptId)?.state ?? null;
  }

  isCancelled(attemptId: string): boolean {
    return this.#attempts.get(attemptId)?.cancellation !== null;
  }

  /**
   * Idempotent: repeat cancellation returns the same terminal record rather
   * than inventing a second one. Each attempt has exactly one terminal outcome.
   */
  cancel(attemptId: string): CancellationRecord {
    const entry = this.#attempts.get(attemptId);

    if (!entry) {
      // An unknown attempt has provably sent nothing through this gateway.
      return {
        attemptId,
        transferState: 'pre-transfer',
        bytesTransferred: 0,
        recallClaimed: false,
        downstreamDeletionRequested: false,
        resultSuppressed: false,
        cancelledAt: this.#now().toISOString(),
      };
    }

    if (entry.cancellation) return entry.cancellation;

    entry.controller.abort();

    const transferred = entry.state === 'transferred';
    const record: CancellationRecord = {
      attemptId,
      transferState: entry.state,
      bytesTransferred: entry.state === 'pre-transfer' ? 0 : entry.bytes,
      recallClaimed: false,
      // Only meaningful when a provider actually received the payload.
      downstreamDeletionRequested: transferred && entry.providerCalled,
      resultSuppressed: transferred,
      cancelledAt: this.#now().toISOString(),
    };
    entry.cancellation = record;
    return record;
  }

  /** Called from the route's `finally`, so every terminal path releases state. */
  release(attemptId: string): void {
    this.#attempts.delete(attemptId);
  }

  get activeCount(): number {
    return this.#attempts.size;
  }
}

/** HTTP status per contract: 204 when nothing was sent, 202 otherwise. */
export const cancellationStatus = (record: CancellationRecord): number =>
  record.transferState === 'pre-transfer' ? 204 : 202;
