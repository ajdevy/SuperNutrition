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

/**
 * How long a finished attempt's terminal record outlives the request.
 *
 * The window matters because of when clients actually cancel: the ordinary
 * sequence is send photo -> receive 200 -> ask for the bytes to be deleted. That
 * cancellation arrives after the pipeline's `finally` has already run, so
 * without retention the gateway has nothing left to answer from.
 *
 * Matched to EFFECT_TTL_SECONDS (inference.handler.ts), the at-most-once
 * horizon: for exactly as long as the gateway refuses to re-run an attemptId it
 * can also say what that attemptId did. Past the horizon it says nothing rather
 * than guessing.
 */
export const TERMINAL_RETENTION_SECONDS = 86_400;

interface AttemptEntry {
  state: TransferState;
  bytes: number;
  providerCalled: boolean;
  /** True once a 200 carrying the result was produced for the client. */
  resultDelivered: boolean;
  controller: AbortController;
  cancellation: CancellationRecord | null;
  /** Epoch ms when the request finished, or null while it is still running. */
  releasedAt: number | null;
}

export class TransferStateTracker {
  readonly #attempts = new Map<string, AttemptEntry>();
  readonly #now: () => Date;

  constructor(now: () => Date = () => new Date()) {
    this.#now = now;
  }

  /**
   * Drops retained records past the horizon. Without this, one entry per attempt
   * would accumulate for the lifetime of the process.
   */
  #sweep(): void {
    const cutoff = this.#now().getTime() - TERMINAL_RETENTION_SECONDS * 1000;
    for (const [attemptId, entry] of this.#attempts) {
      if (entry.releasedAt !== null && entry.releasedAt <= cutoff) {
        this.#attempts.delete(attemptId);
      }
    }
  }

  begin(attemptId: string): AbortController {
    this.#sweep();
    const controller = new AbortController();
    this.#attempts.set(attemptId, {
      state: 'pre-transfer',
      bytes: 0,
      providerCalled: false,
      resultDelivered: false,
      controller,
      cancellation: null,
      releasedAt: null,
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

  /** Recorded just before the 200 is produced, so a later cancellation can tell
   * "the client already has this result" from "the result was thrown away". */
  markResultDelivered(attemptId: string): void {
    const entry = this.#attempts.get(attemptId);
    if (!entry) return;
    entry.resultDelivered = true;
  }

  stateOf(attemptId: string): TransferState | null {
    return this.#attempts.get(attemptId)?.state ?? null;
  }

  isCancelled(attemptId: string): boolean {
    const entry = this.#attempts.get(attemptId);
    // Written as an explicit lookup because the optional-chained form,
    // `get(id)?.cancellation !== null`, evaluates `undefined !== null` for an
    // attempt that is not there and answers "cancelled" for every attempt this
    // gateway has never seen. The pipeline uses this to decide whether to throw
    // a completed result away.
    if (!entry) return false;
    return entry.cancellation !== null;
  }

  /**
   * Idempotent: repeat cancellation returns the same terminal record rather
   * than inventing a second one. Each attempt has exactly one terminal outcome.
   *
   * Null means the gateway holds no record of this attempt — it was never begun,
   * or it finished longer ago than TERMINAL_RETENTION_SECONDS. That is not the
   * same as "nothing was sent", and the caller must not turn it into one.
   */
  cancel(attemptId: string): CancellationRecord | null {
    this.#sweep();
    const entry = this.#attempts.get(attemptId);
    if (!entry) return null;
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
      // A result already returned in a 200 is not discarded by a later
      // cancellation. Claiming otherwise is the same false comfort as reporting
      // a completed attempt as pre-transfer.
      resultSuppressed: transferred && !entry.resultDelivered,
      cancelledAt: this.#now().toISOString(),
    };
    entry.cancellation = record;
    return record;
  }

  /**
   * Called from the route's `finally`, so every terminal path releases state.
   *
   * The entry is retained rather than deleted. Deleting it sent the ordinary
   * cancel-after-200 down the unknown-attempt path, where the gateway answered
   * "pre-transfer, 0 bytes, 204" — by contract a complete guarantee that nothing
   * was sent — about a photo a provider had already received.
   */
  release(attemptId: string): void {
    const entry = this.#attempts.get(attemptId);
    if (!entry) return;
    entry.releasedAt = this.#now().getTime();
    this.#sweep();
  }

  /** Attempts still running. Retained terminal records are not active work. */
  get activeCount(): number {
    let live = 0;
    for (const entry of this.#attempts.values()) {
      if (entry.releasedAt === null) live += 1;
    }
    return live;
  }

  /** Finished attempts still inside the retention window. */
  get retainedCount(): number {
    let retained = 0;
    for (const entry of this.#attempts.values()) {
      if (entry.releasedAt !== null) retained += 1;
    }
    return retained;
  }
}

/** HTTP status per contract: 204 when nothing was sent, 202 otherwise. */
export const cancellationStatus = (record: CancellationRecord): number =>
  record.transferState === 'pre-transfer' ? 204 : 202;
