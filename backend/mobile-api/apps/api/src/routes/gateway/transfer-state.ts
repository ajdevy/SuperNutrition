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
  /**
   * H-02 — set only by an actual, provider-supported deletion invocation.
   *
   * Separate from providerCalled because they answer different questions: one is
   * "did bytes leave", the other is "did we ask for them back". The response
   * field is the second, and it used to report the first.
   */
  deletionRequested: boolean;
  /** True once a 200 carrying the result was produced for the client. */
  resultDelivered: boolean;
  controller: AbortController;
  cancellation: CancellationRecord | null;
  /** Epoch ms when the request finished, or null while it is still running. */
  releasedAt: number | null;
  /**
   * The installation that started this attempt.
   *
   * Attempt ids are chosen by the client, so without an owner any authenticated
   * caller could cancel — and read the transfer record of — any attempt id they
   * cared to guess.
   */
  owner: string;
}

/**
 * H-03 — a finished attempt that never left `pre-transfer` does not block a
 * retry of the same id.
 *
 * `begin()` refuses an id it already holds, which is M7's at-most-once on the
 * attempt: the same photo must not be sent twice. A retained record that never
 * reached `transferring` describes an attempt where NOTHING was sent, so that
 * concern does not apply to it — and refusing anyway turned one transient Redis
 * failure during the claim into a 24-hour block on that attempt id, answering a
 * client's honest retry with `attempt_already_used` about an attempt that never
 * happened.
 *
 * The record itself is kept, so a cancellation can still make the true and
 * useful `pre-transfer` statement ("Nothing was sent. This is a complete
 * guarantee."). Only the claim on the id is released.
 *
 * Three conditions, all required: the request must be finished (a live attempt
 * is still a live attempt), it must never have left pre-transfer, and it must
 * not already carry a terminal cancellation record — that record is an answer
 * the gateway has committed to and a new attempt would contradict it.
 */
const isRetryableRemnant = (entry: AttemptEntry): boolean =>
  entry.releasedAt !== null &&
  entry.state === 'pre-transfer' &&
  !entry.providerCalled &&
  entry.cancellation === null;

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

  /**
   * Claims an attempt id for one transfer, or returns null if it is taken.
   *
   * Null rather than an overwrite. The effect store enforces at-most-once on
   * effectId, so the same attemptId carrying a fresh effectId walked straight
   * past it — and the old begin() then replaced the live entry, orphaning the
   * first request's AbortController so nothing could cancel it any more. The
   * claim now covers the whole retention window: for as long as the gateway can
   * describe an attempt, it also refuses to run that id a second time.
   */
  begin(attemptId: string, owner: string): AbortController | null {
    this.#sweep();
    const existing = this.#attempts.get(attemptId);
    if (existing !== undefined && !isRetryableRemnant(existing)) return null;

    const controller = new AbortController();
    this.#attempts.set(attemptId, {
      state: 'pre-transfer',
      bytes: 0,
      providerCalled: false,
      deletionRequested: false,
      resultDelivered: false,
      controller,
      cancellation: null,
      releasedAt: null,
      owner,
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

  /**
   * Record that a deletion was actually requested of the provider.
   *
   * Nothing calls this yet: no adapter exposes an erasure operation. It exists
   * so the flag can only ever become true by way of a real invocation, rather
   * than being inferred from the fact that a transfer happened.
   *
   * So `downstreamDeletionRequested` is constant `false` in production today,
   * and that is the correct answer — no deletion is requested of any provider,
   * because no provider request can carry one. It previously reported `true`
   * whenever a provider had merely been *called*, which was a privacy claim the
   * product could not support.
   *
   * The capability itself is gated on the provider/DPA decision (handover-3
   * deferred item 2, G-4): the current OpenRouter request body has no
   * per-request retention or erasure control. When an adapter gains one, it
   * calls this; until then a caller that wants the flag true has to add the
   * real thing first, which is the point.
   */
  markDownstreamDeletionRequested(attemptId: string): void {
    const entry = this.#attempts.get(attemptId);
    if (!entry) return;
    entry.deletionRequested = true;
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
  cancel(attemptId: string, owner: string): CancellationRecord | null {
    this.#sweep();
    const entry = this.#attempts.get(attemptId);
    if (!entry) return null;
    // A cancellation from another installation is answered identically to one
    // for an attempt that does not exist. A distinguishable "forbidden" would
    // let a caller enumerate which attempt ids are real.
    if (entry.owner !== owner) return null;
    if (entry.cancellation) return entry.cancellation;

    entry.controller.abort();

    const transferred = entry.state === 'transferred';
    const record: CancellationRecord = {
      attemptId,
      transferState: entry.state,
      bytesTransferred: entry.state === 'pre-transfer' ? 0 : entry.bytes,
      recallClaimed: false,
      // Only meaningful when a provider actually received the payload.
      // H-02 — this was `transferred && providerCalled`: true the moment a
      // provider had been called. The release says it is true only when a
      // provider received the payload AND a deletion request was issued, and
      // the provider port exposes inference only, so there is no deletion to
      // invoke. The field asserted an erasure nobody had asked for, in the one
      // response a user reads to learn what became of their photo.
      //
      // Until an adapter supports deletion this is false, which is the honest
      // answer the release's honest-limit wording depends on.
      downstreamDeletionRequested: transferred && entry.deletionRequested,
      // True whenever inference had produced, or would produce, a result that
      // this cancellation discards — which is the release's own definition.
      //
      // This read `transferred && !resultDelivered`. Once #48 stopped promoting
      // the state at hand-off, a cancellation arriving while the provider call
      // was on the wire saw `transferring` and reported `false` — "no result was
      // thrown away" — about an attempt whose result the pipeline then threw
      // away on the very next line it ran. The formula made `true` structurally
      // impossible for the commonest cancellation there is.
      //
      // A result already returned in a 200 is still not discarded by a later
      // cancellation, which is what `resultDelivered` continues to guard.
      resultSuppressed:
        (entry.state === 'transferring' || entry.state === 'transferred') && !entry.resultDelivered,
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
