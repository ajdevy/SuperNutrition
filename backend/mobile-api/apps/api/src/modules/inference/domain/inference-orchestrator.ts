import { type Result, ok, err } from '@supernutrition/shared';
import type {
  Candidate,
  InferenceFailure,
  InferenceRequest,
  InferenceResult,
} from './inference.types.js';
import { isRetryable } from './inference.types.js';
import type { InferenceProvider } from './inference-provider.port.js';
import type {
  InferenceAttemptOptions,
  InferenceOrchestrator,
  InferenceRunRecorder,
  InferenceRunTelemetry,
} from './inference-orchestrator.port.js';
import { bucketDuration } from './inference-orchestrator.port.js';
import type { ModelProfile, ModelProfileSource } from './model-profile.js';

/**
 * Two-pass confidence routing (docs/07 §7.5).
 *
 * Three properties are easy to lose here and are asserted by tests:
 *
 * 1. Pass 2 reuses the payload already received for the SAME attempt. It is not
 *    a new transfer and requires no new consent receipt — treating it as one
 *    would prompt the user for something they already agreed to.
 * 2. Low confidence is not a failure. It returns candidates flagged
 *    `requiresConfirmation`, because the user is the authority and the honest
 *    result of uncertainty is a question.
 * 3. Every pass writes exactly one telemetry row, including failures.
 */

export interface Clock {
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

export interface OrchestratorDeps {
  readonly providers: readonly InferenceProvider[];
  readonly profiles: ModelProfileSource;
  readonly recorder: InferenceRunRecorder;
  readonly environment: string;
  readonly clock?: Clock;
  readonly sleep?: (ms: number, signal: AbortSignal) => Promise<void>;
  /** Injected so retry jitter is deterministic under test. */
  readonly random?: () => number;
}

const MAX_ATTEMPTS_PER_PASS = 2;
const BASE_BACKOFF_MS = 200;

const defaultSleep = (ms: number, signal: AbortSignal): Promise<void> =>
  new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new Error('aborted'));
      return;
    }
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(new Error('aborted'));
    };
    signal.addEventListener('abort', onAbort, { once: true });
  });

interface PassOutcome {
  readonly candidates: readonly Candidate[];
  readonly confidence: number | null;
  readonly modelId: string;
  readonly outcome: 'candidates' | 'abstention';
}

export class TwoPassInferenceOrchestrator implements InferenceOrchestrator {
  readonly #deps: OrchestratorDeps;
  readonly #clock: Clock;
  readonly #sleep: (ms: number, signal: AbortSignal) => Promise<void>;
  readonly #random: () => number;

  constructor(deps: OrchestratorDeps) {
    this.#deps = deps;
    this.#clock = deps.clock ?? systemClock;
    this.#sleep = deps.sleep ?? defaultSleep;
    this.#random = deps.random ?? Math.random;
  }

  async analyze(
    request: InferenceRequest,
    options: InferenceAttemptOptions,
  ): Promise<Result<InferenceResult, InferenceFailure>> {
    const snapshot = this.#deps.profiles.current();
    const startedAt = this.#clock.now();
    const deadlineAt = startedAt + snapshot.routing.totalDeadlineMs;

    const pass1 = await this.#runPass(1, request, options, deadlineAt, snapshot.profileVersion);
    if (!pass1.ok) {
      // A terminal failure on pass 1 ends the attempt: there is nothing to
      // escalate, and retrying a rejected payload only burns cost.
      return pass1;
    }

    const threshold = snapshot.routing.confidenceThreshold;
    if ((pass1.value.confidence ?? 0) >= threshold) {
      return ok(this.#accept(request, pass1.value, startedAt, 1, false));
    }

    // An abstention is an ANSWER, not a low-confidence identification.
    //
    // It carries no confidence, so `?? 0` scored it below every threshold and it
    // escalated to the second, more expensive model — which is then asked to
    // identify food in a photo the first model said contains none. That buys a
    // second opinion on a question already answered, at the cost of the pass-2
    // model on every foodless photo a user happens to take.
    if (pass1.value.outcome === 'abstention') {
      return ok(this.#accept(request, pass1.value, startedAt, 1, false));
    }

    const budgetLeft = deadlineAt - this.#clock.now();
    if (snapshot.routing.maxPasses < 2 || budgetLeft <= 0 || options.signal.aborted) {
      return ok(this.#accept(request, pass1.value, startedAt, 1, true));
    }

    const pass2 = await this.#runPass(2, request, options, deadlineAt, snapshot.profileVersion);
    if (!pass2.ok) {
      // Pass 2 failing does not discard pass 1 — the user still gets the best
      // available evidence, flagged for confirmation.
      return ok(this.#accept(request, pass1.value, startedAt, 2, true));
    }

    if ((pass2.value.confidence ?? 0) >= threshold) {
      return ok(this.#accept(request, pass2.value, startedAt, 2, false));
    }

    const best =
      (pass2.value.confidence ?? 0) >= (pass1.value.confidence ?? 0) ? pass2.value : pass1.value;
    return ok(this.#accept(request, best, startedAt, 2, true));
  }

  #accept(
    request: InferenceRequest,
    pass: PassOutcome,
    startedAt: number,
    passesRun: number,
    requiresConfirmation: boolean,
  ): InferenceResult {
    return {
      attemptId: request.attemptId,
      outcome: pass.outcome,
      candidates: pass.candidates,
      // Both were hardcoded. `confidence: null` discarded the number the
      // routing decision above was just made on, and `durationMs: 0` reported
      // every attempt as instantaneous — so the telemetry that exists to show
      // how long inference took, and how sure it was, said nothing at all.
      confidence: pass.confidence as InferenceResult['confidence'],
      modelId: pass.modelId,
      durationMs: Math.max(0, this.#clock.now() - startedAt),
      passesRun,
      requiresConfirmation: pass.outcome === 'candidates' ? requiresConfirmation : false,
    };
  }

  async #runPass(
    pass: 1 | 2,
    request: InferenceRequest,
    options: InferenceAttemptOptions,
    deadlineAt: number,
    profileVersion: string,
  ): Promise<Result<PassOutcome, InferenceFailure>> {
    const snapshot = this.#deps.profiles.current();
    const profiles = this.#deps.profiles.profilesForPass(pass).filter((p) => p.enabled);
    if (profiles.length === 0) return err('provider-unavailable');

    let lastFailure: InferenceFailure = 'provider-unavailable';

    for (const profile of profiles) {
      const provider = this.#deps.providers.find((p) => p.supports(profile));
      if (!provider) continue;

      for (let attempt = 1; attempt <= MAX_ATTEMPTS_PER_PASS; attempt += 1) {
        if (options.signal.aborted) return err('cancelled');

        const remaining = deadlineAt - this.#clock.now();
        if (remaining <= 0) return err('timeout');

        const passDeadline = Math.min(remaining, snapshot.routing.passDeadlineMs);
        const startedAt = this.#clock.now();

        const response = await provider.infer({
          attemptId: request.attemptId,
          modality: request.modality,
          payload: request.payload,
          context: request.context,
          profile,
          deadlineMs: passDeadline,
          signal: options.signal,
        });

        const durationMs = this.#clock.now() - startedAt;

        // #48(a) — the provider has now returned, which is the first moment the
        // payload is known to have been admitted rather than merely dialled. A
        // refusal, timeout, or reset does NOT fire this: the caller must not be
        // told a transfer completed that did not.
        //
        // Fired once per attempt. Pass 2 reuses a payload already admitted, and
        // re-firing would report a second transfer of one photo.
        if (response.ok && pass === 1) {
          options.onProviderAdmitted?.();
        }

        if (response.ok) {
          const best = response.value.candidates[0];
          const confidence = best?.confidence.score ?? null;
          await this.#record({
            request,
            options,
            pass,
            profile,
            profileVersion,
            outcome: response.value.outcome,
            success: true,
            confidence,
            durationMs,
            promptTokens: response.value.usage.promptTokens,
            completionTokens: response.value.usage.completionTokens,
            modelId: response.value.modelId,
            failureCode: null,
          });

          return ok({
            candidates: response.value.candidates,
            confidence,
            modelId: response.value.modelId,
            outcome: response.value.outcome,
          });
        }

        lastFailure = response.error;
        await this.#record({
          request,
          options,
          pass,
          profile,
          profileVersion,
          outcome: 'failure',
          success: false,
          confidence: null,
          durationMs,
          promptTokens: null,
          completionTokens: null,
          modelId: profile.model,
          failureCode: response.error,
        });

        if (!isRetryable(response.error)) break;
        if (attempt === MAX_ATTEMPTS_PER_PASS) break;

        // Exponential backoff with full jitter, always inside the remaining
        // budget: a retry that outlives the client's own timeout is pure cost.
        const backoff = Math.min(
          BASE_BACKOFF_MS * 2 ** (attempt - 1) * this.#random(),
          Math.max(0, deadlineAt - this.#clock.now()),
        );
        if (backoff <= 0) return err('timeout');
        try {
          await this.#sleep(backoff, options.signal);
        } catch {
          return err('cancelled');
        }
      }

      if (!isRetryable(lastFailure)) break;
    }

    return err(lastFailure);
  }

  async #record(input: {
    request: InferenceRequest;
    options: InferenceAttemptOptions;
    pass: 1 | 2;
    profile: ModelProfile;
    profileVersion: string;
    outcome: 'candidates' | 'abstention' | 'failure';
    success: boolean;
    confidence: number | null;
    durationMs: number;
    promptTokens: number | null;
    completionTokens: number | null;
    modelId: string;
    failureCode: string | null;
  }): Promise<void> {
    const cost =
      input.promptTokens !== null && input.completionTokens !== null
        ? BigInt(
            Math.round(
              (input.promptTokens / 1000) * input.profile.costPer1kInputMicros +
                (input.completionTokens / 1000) * input.profile.costPer1kOutputMicros,
            ),
          )
        : null;

    const telemetry: InferenceRunTelemetry = {
      attemptId: input.request.attemptId,
      passNumber: input.pass,
      provider: input.profile.provider,
      modelId: input.modelId,
      modelProfileVersion: input.profileVersion,
      outcome: input.outcome,
      success: input.success,
      confidence: input.confidence,
      durationMs: input.durationMs,
      durationBucket: bucketDuration(input.durationMs),
      promptTokens: input.promptTokens,
      completionTokens: input.completionTokens,
      estimatedCostMicros: cost,
      failureCode: input.failureCode,
      marketJurisdiction: input.options.marketJurisdiction,
      processingRegion: input.options.processingRegion,
      environment: this.#deps.environment,
    };

    // Telemetry must never fail the attempt. A metrics outage is not a user
    // problem, and the meal-logging path is the primary KPI.
    try {
      await this.#deps.recorder.record(telemetry);
    } catch {
      /* swallowed deliberately */
    }
  }
}
