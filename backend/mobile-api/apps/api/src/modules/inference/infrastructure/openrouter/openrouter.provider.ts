import { type Result, ok, err } from '@supernutrition/shared';
import type {
  InferenceProvider,
  ProviderRequest,
  ProviderResponse,
} from '../../domain/inference-provider.port.js';
import type {
  InferenceFailure,
  PayloadSource,
  ProviderType,
} from '../../domain/inference.types.js';
import type { ModelProfile } from '../../domain/model-profile.js';
import { FOOD_IDENTIFICATION_PROMPT, PROMPT_VERSION } from './prompts/food-identification.js';
import { mapProviderResponse, mapHttpFailure } from './response.mapper.js';

/**
 * The ONLY place in the product that may know OpenRouter exists.
 *
 * The API key is held in a private field set at construction. It is not on a
 * config object other modules can reach, not in a global, and not enumerable —
 * `Object.keys()` and `JSON.stringify()` on the instance reveal nothing
 * (docs/09 §9.8).
 *
 * Nothing provider-shaped escapes this directory: `response.mapper.ts` is the
 * sole translation point to contract-v1 evidence (AD-13).
 */

export interface OpenRouterConfig {
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly referer?: string;
  readonly title?: string;
}

export type FetchLike = (input: string, init: RequestInit) => Promise<Response>;

export class OpenRouterInferenceProvider implements InferenceProvider {
  readonly id: ProviderType = 'openrouter';

  readonly #apiKey: string;
  readonly #config: OpenRouterConfig;
  readonly #payloads: PayloadSource;
  readonly #fetch: FetchLike;

  constructor(
    apiKey: string,
    config: OpenRouterConfig,
    payloads: PayloadSource,
    fetchImpl?: FetchLike,
  ) {
    this.#apiKey = apiKey;
    this.#config = config;
    this.#payloads = payloads;
    this.#fetch = fetchImpl ?? ((input, init) => fetch(input, init));
  }

  supports(profile: ModelProfile): boolean {
    return profile.provider === 'openrouter' && profile.enabled;
  }

  async infer(request: ProviderRequest): Promise<Result<ProviderResponse, InferenceFailure>> {
    if (request.signal.aborted) return err('cancelled');

    // Bytes are read at the last possible moment and never retained. A disposed
    // payload resolves to null, so a late retry cannot resurrect it.
    const bytes = this.#payloads.read(request.payload);
    if (bytes === null) return err('cancelled');

    const started = Date.now();
    const controller = new AbortController();
    const onAbort = () => controller.abort();
    request.signal.addEventListener('abort', onAbort, { once: true });
    const timer = setTimeout(
      () => controller.abort(),
      Math.max(1, Math.min(request.deadlineMs, this.#config.timeoutMs)),
    );

    try {
      // #48 — the bytes are about to leave this process. Signalled from here
      // because only the adapter knows: the pipeline cannot tell a configured
      // provider from an absent one.
      request.onTransferStarted?.();

      const response = await this.#fetch(`${this.#config.baseUrl}/chat/completions`, {
        method: 'POST',
        signal: controller.signal,
        headers: {
          Authorization: `Bearer ${this.#apiKey}`,
          'Content-Type': 'application/json',
          ...(this.#config.referer ? { 'HTTP-Referer': this.#config.referer } : {}),
          ...(this.#config.title ? { 'X-Title': this.#config.title } : {}),
        },
        body: JSON.stringify(this.#buildBody(request, bytes)),
      });

      // #48 — the provider ANSWERED, so it received the payload. Fired before
      // the status is examined, and deliberately not gated on `response.ok`: a
      // 400, a 422, or a 200 carrying an unmappable body all mean the image
      // arrived and was processed. Only a request that never got a response —
      // refused connection, timeout, abort — leaves this unfired.
      request.onProviderAdmitted?.();

      if (!response.ok) {
        // The body is drained and discarded: provider error prose must never
        // reach a domain type or a log line.
        await response.text().catch(() => '');
        return err(mapHttpFailure(response.status));
      }

      const payload: unknown = await response.json().catch(() => null);
      if (payload === null) return err('malformed-response');

      const mapped = mapProviderResponse(payload, request.profile);
      if (!mapped.ok) return err(mapped.error);

      return ok({
        candidates: mapped.value.candidates,
        modelId: mapped.value.modelId,
        durationMs: Date.now() - started,
        usage: mapped.value.usage,
        outcome: mapped.value.candidates.length > 0 ? 'candidates' : 'abstention',
      });
    } catch (error) {
      if (request.signal.aborted) return err('cancelled');
      if (error instanceof Error && error.name === 'AbortError') return err('timeout');
      return err('provider-unavailable');
    } finally {
      clearTimeout(timer);
      request.signal.removeEventListener('abort', onAbort);
    }
  }

  #buildBody(request: ProviderRequest, bytes: Uint8Array): Record<string, unknown> {
    const { profile, payload, context } = request;
    const dataUrl = `data:${payload.contentType};base64,${Buffer.from(bytes).toString('base64')}`;

    return {
      model: profile.model,
      max_tokens: profile.maxOutputTokens,
      temperature: profile.temperature,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: FOOD_IDENTIFICATION_PROMPT },
        {
          role: 'user',
          content: [
            {
              type: 'text',
              // User-derived values go in as delimited JSON data under an
              // explicitly untrusted key, never concatenated into instructions.
              text: JSON.stringify({
                promptVersion: PROMPT_VERSION,
                untrustedContext: {
                  catalogCountry: context.catalogCountry,
                  mealType: context.mealType,
                  locale: context.locale,
                  measurementSystem: context.measurementSystem,
                  priorMealSummary: context.priorMealSummary ?? null,
                },
              }),
            },
            { type: 'image_url', image_url: { url: dataUrl } },
          ],
        },
      ],
    };
  }
}

/**
 * In-request payload buffer.
 *
 * Owns the only reference to image bytes for an attempt, and `dispose` is
 * idempotent and unconditional — the route calls it in a `finally`, so success,
 * error, timeout, and cancellation all take the same path (AD-8, docs/09 §9.6).
 */
export class InMemoryPayloadSource implements PayloadSource {
  readonly #buffers = new Map<string, Uint8Array>();

  put(attemptId: string, bytes: Uint8Array): void {
    this.#buffers.set(attemptId, bytes);
  }

  read(ref: { attemptId: string }): Uint8Array | null {
    return this.#buffers.get(ref.attemptId) ?? null;
  }

  dispose(attemptId: string): void {
    const existing = this.#buffers.get(attemptId);
    if (existing) existing.fill(0);
    this.#buffers.delete(attemptId);
  }

  get size(): number {
    return this.#buffers.size;
  }
}
