import { describe, it, expect } from 'vitest';
import { ok, err, type Result } from '@supernutrition/shared';
import { TwoPassInferenceOrchestrator } from './inference-orchestrator.js';
import type {
  InferenceProvider,
  ProviderRequest,
  ProviderResponse,
} from './inference-provider.port.js';
import type { InferenceRunRecorder, InferenceRunTelemetry } from './inference-orchestrator.port.js';
import type { InferenceFailure, InferenceRequest } from './inference.types.js';
import type { ModelProfile, ModelProfileSnapshot, ModelProfileSource } from './model-profile.js';

const profile = (id: string, pass: 1 | 2): ModelProfile => ({
  id,
  pass,
  provider: 'openrouter',
  model: `model-${id}`,
  maxOutputTokens: 512,
  temperature: 0,
  costPer1kInputMicros: 100,
  costPer1kOutputMicros: 200,
  enabled: true,
});

const snapshot: ModelProfileSnapshot = {
  profileVersion: 'test/1',
  routing: {
    confidenceThreshold: 0.75,
    acceptBand: 'high',
    maxPasses: 2,
    passDeadlineMs: 8000,
    totalDeadlineMs: 20000,
  },
  profiles: [profile('fast', 1), profile('fast-alt', 1), profile('deep', 2)],
};

const profiles: ModelProfileSource = {
  current: () => snapshot,
  profilesForPass: (pass) => snapshot.profiles.filter((p) => p.pass === pass),
};

const candidate = (score: number) => ({
  candidateId: 'cand_01',
  label: 'Banana',
  rank: 1,
  confidence: {
    score,
    band: score >= 0.75 ? 'high' : 'low',
    engineId: 'e',
    calibrationVersion: 'c',
  } as never,
  unresolvedAssumptions: [],
  provenance: {
    route: 'remote' as const,
    engineId: 'e',
    modelVersion: 'm',
    calibrationVersion: 'c',
    contractVersion: 'photo-inference-engine/v1',
  },
});

class ScriptedProvider implements InferenceProvider {
  readonly id = 'openrouter' as const;
  readonly seen: ProviderRequest[] = [];
  #script: Result<ProviderResponse, InferenceFailure>[];

  constructor(script: Result<ProviderResponse, InferenceFailure>[]) {
    this.#script = script;
  }

  supports(p: ModelProfile): boolean {
    return p.provider === 'openrouter' && p.enabled;
  }

  async infer(request: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>> {
    this.seen.push(request);
    return this.#script.shift() ?? err('provider-unavailable');
  }
}

class RecordingRecorder implements InferenceRunRecorder {
  readonly rows: InferenceRunTelemetry[] = [];
  async record(run: InferenceRunTelemetry): Promise<void> {
    this.rows.push(run);
  }
}

const response = (score: number): Result<ProviderResponse, InferenceFailure> =>
  ok({
    candidates: [candidate(score)],
    modelId: 'model-fast',
    durationMs: 10,
    usage: { promptTokens: 1000, completionTokens: 500 },
    outcome: 'candidates',
  });

const request: InferenceRequest = {
  attemptId: 'att_0000000000000000' as never,
  modality: 'photo',
  payload: {
    attemptId: 'att_0000000000000000' as never,
    byteLength: 1024,
    contentType: 'image/jpeg',
    digest: 'sha256:x',
  },
  context: {
    catalogCountry: 'ZZ',
    mealType: 'lunch',
    locale: 'en-GB',
    measurementSystem: 'metric',
  },
  contractVersion: 'mobile-api/v1',
};

const build = (provider: ScriptedProvider, recorder = new RecordingRecorder()) =>
  new TwoPassInferenceOrchestrator({
    providers: [provider],
    profiles,
    recorder,
    environment: 'test',
    sleep: async () => {},
    random: () => 0.5,
  });

const options = (signal = new AbortController().signal) => ({
  signal,
  marketJurisdiction: 'ZZ',
  processingRegion: 'fixture-region',
});

describe('confidence routing', () => {
  it('accepts pass 1 above threshold without a second pass', async () => {
    const provider = new ScriptedProvider([response(0.93)]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.passesRun).toBe(1);
    expect(result.value.requiresConfirmation).toBe(false);
    expect(provider.seen).toHaveLength(1);
  });

  it('escalates to pass 2 when pass 1 is below threshold', async () => {
    const provider = new ScriptedProvider([response(0.4), response(0.88)]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.passesRun).toBe(2);
    expect(result.value.requiresConfirmation).toBe(false);
  });

  // Low confidence is NOT a failure: the user is the authority, so the honest
  // result of uncertainty is a question rather than an error.
  it('returns candidates flagged for confirmation when both passes are low', async () => {
    const provider = new ScriptedProvider([response(0.3), response(0.45)]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.requiresConfirmation).toBe(true);
    expect(result.value.candidates).toHaveLength(1);
  });

  it('keeps the better of the two passes', async () => {
    const provider = new ScriptedProvider([response(0.6), response(0.2)]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.candidates[0]?.confidence.score).toBe(0.6);
  });

  // Pass 2 reuses the payload already transferred for the SAME attempt. It is
  // not a new transfer and requires no new consent receipt.
  it('reuses the same payload ref for pass 2', async () => {
    const provider = new ScriptedProvider([response(0.3), response(0.4)]);
    await build(provider).analyze(request, options());

    expect(provider.seen).toHaveLength(2);
    expect(provider.seen[1]?.payload).toEqual(provider.seen[0]?.payload);
    expect(provider.seen[1]?.attemptId).toBe(provider.seen[0]?.attemptId);
  });

  it('still returns pass 1 evidence when pass 2 fails', async () => {
    const provider = new ScriptedProvider([
      response(0.3),
      err('provider-unavailable'),
      err('provider-unavailable'),
    ]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.value.requiresConfirmation).toBe(true);
  });
});

describe('retry policy', () => {
  it('retries a transient failure then succeeds', async () => {
    const provider = new ScriptedProvider([err('timeout'), response(0.9)]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(true);
    expect(provider.seen).toHaveLength(2);
  });

  it('does not retry a terminal failure', async () => {
    const provider = new ScriptedProvider([err('content-rejected')]);
    const result = await build(provider).analyze(request, options());

    expect(result.ok).toBe(false);
    // One call on the first profile only: a rejected payload is rejected by
    // every model, so trying the fallback would just spend money.
    expect(provider.seen).toHaveLength(1);
  });

  it('caps attempts per pass', async () => {
    const provider = new ScriptedProvider([
      err('timeout'),
      err('timeout'),
      err('timeout'),
      err('timeout'),
      err('timeout'),
      err('timeout'),
    ]);
    const result = await build(provider).analyze(request, options());
    expect(result.ok).toBe(false);
    // 2 attempts on each of the two pass-1 profiles, and no more.
    expect(provider.seen).toHaveLength(4);
  });
});

describe('cancellation', () => {
  it('returns cancelled without calling a provider when already aborted', async () => {
    const controller = new AbortController();
    controller.abort();
    const provider = new ScriptedProvider([response(0.9)]);
    const result = await build(provider).analyze(request, options(controller.signal));

    expect(result).toEqual({ ok: false, error: 'cancelled' });
    expect(provider.seen).toHaveLength(0);
  });
});

describe('telemetry (AD-14)', () => {
  it('writes exactly one row per pass', async () => {
    const recorder = new RecordingRecorder();
    const provider = new ScriptedProvider([response(0.3), response(0.4)]);
    await build(provider, recorder).analyze(request, options());

    expect(recorder.rows).toHaveLength(2);
    expect(recorder.rows.map((row) => row.passNumber)).toEqual([1, 2]);
  });

  it('records failures too', async () => {
    const recorder = new RecordingRecorder();
    const provider = new ScriptedProvider([err('content-rejected')]);
    await build(provider, recorder).analyze(request, options());

    expect(recorder.rows).toHaveLength(1);
    expect(recorder.rows[0]).toMatchObject({ success: false, failureCode: 'content-rejected' });
  });

  it('carries no user identity, prompt, or response body', async () => {
    const recorder = new RecordingRecorder();
    await build(new ScriptedProvider([response(0.9)]), recorder).analyze(request, options());

    const row = recorder.rows[0] as Record<string, unknown>;
    for (const forbidden of ['userId', 'user_id', 'prompt', 'responseBody', 'label', 'payload']) {
      expect(row, `leaked ${forbidden}`).not.toHaveProperty(forbidden);
    }
  });

  it('buckets duration rather than exposing a raw trace', async () => {
    const recorder = new RecordingRecorder();
    await build(new ScriptedProvider([response(0.9)]), recorder).analyze(request, options());
    expect(['under-1s', '1s-3s', '3s-10s', 'over-10s']).toContain(recorder.rows[0]?.durationBucket);
  });

  it('estimates cost from token usage and the profile rates', async () => {
    const recorder = new RecordingRecorder();
    await build(new ScriptedProvider([response(0.9)]), recorder).analyze(request, options());
    // 1000 prompt tokens at 100 micros/1k + 500 completion at 200 micros/1k.
    expect(recorder.rows[0]?.estimatedCostMicros).toBe(200n);
  });

  // A metrics outage is not a user problem, and meal logging is the primary KPI.
  it('never fails the attempt when the recorder throws', async () => {
    const throwing: InferenceRunRecorder = {
      record: async () => {
        throw new Error('telemetry down');
      },
    };
    const result = await new TwoPassInferenceOrchestrator({
      providers: [new ScriptedProvider([response(0.9)])],
      profiles,
      recorder: throwing,
      environment: 'test',
      sleep: async () => {},
      random: () => 0.5,
    }).analyze(request, options());

    expect(result.ok).toBe(true);
  });
});

describe('no provider available', () => {
  it('fails closed when no profile matches', async () => {
    const orchestrator = new TwoPassInferenceOrchestrator({
      providers: [],
      profiles,
      recorder: new RecordingRecorder(),
      environment: 'test',
    });
    const result = await orchestrator.analyze(request, options());
    expect(result).toEqual({ ok: false, error: 'provider-unavailable' });
  });
});
