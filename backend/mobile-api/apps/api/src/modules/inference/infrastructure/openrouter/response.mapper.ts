import { type Result, ok, err, createConfidenceScore, isOk } from '@supernutrition/shared';
import type { Candidate, InferenceFailure } from '../../domain/inference.types.js';
import type { TokenUsage } from '../../domain/inference-provider.port.js';
import type { ModelProfile } from '../../domain/model-profile.js';
import { PROMPT_VERSION } from './prompts/food-identification.js';

/**
 * The sole translation point from a provider shape to contract-v1 evidence.
 *
 * No provider type is exported beyond this directory, so a provider schema
 * cannot become the mobile contract (AD-13). Provider error strings are mapped
 * to the closed `InferenceFailure` set and never propagated: a domain that can
 * branch on an upstream message is coupled to that upstream's release notes.
 */

export interface MappedResponse {
  readonly candidates: readonly Candidate[];
  readonly modelId: string;
  readonly usage: TokenUsage;
}

/** Keys the model must never return. Their presence is a contract violation. */
const FORBIDDEN_KEYS = [
  'calories',
  'kcal',
  'energy',
  'protein',
  'proteing',
  'fat',
  'fatg',
  'carb',
  'carbs',
  'carbg',
  'carbohydrate',
  'fiber',
  'fiberg',
  'sugar',
  'sugarg',
  'sodium',
  'sodiummg',
  'nutrients',
  'nutrition',
  'macros',
  'servingbasis',
  'gramweight',
  'serving',
  'weight',
  'portion_grams',
];

const containsForbiddenKey = (node: unknown): boolean => {
  if (Array.isArray(node)) return node.some(containsForbiddenKey);
  if (node !== null && typeof node === 'object') {
    return Object.entries(node as Record<string, unknown>).some(
      ([key, value]) => FORBIDDEN_KEYS.includes(key.toLowerCase()) || containsForbiddenKey(value),
    );
  }
  return false;
};

const ENGINE_ID = 'gateway-openrouter';
const CALIBRATION_VERSION = 'uncalibrated-g5-pending';

export const mapHttpFailure = (status: number): InferenceFailure => {
  if (status === 429) return 'rate-limited';
  if (status === 408 || status === 504) return 'timeout';
  if (status === 402) return 'budget-exceeded';

  // A dead, revoked or misconfigured key. This used to fall through to
  // provider-unavailable, which RETRYABLE_FAILURES lists — so the orchestrator
  // retried a credential that cannot start working, on every request, and the
  // user was told "temporarily unavailable, try again" about an outage that
  // would never clear on its own. kill-switch is the non-retryable "the remote
  // path is not available" signal, and with a dead key that is exactly true.
  if (status === 401 || status === 403) return 'kill-switch';

  // A 400 or 422 is this gateway's request being wrong, not the user's photo.
  // Reporting it as content-rejected put our own bug in front of the user as a
  // verdict on their food and sent them to retake a picture that was never the
  // problem. malformed-response is non-retryable and surfaces as an internal
  // error, which is where an operator will look for it.
  //
  // The cost, stated plainly: a genuinely moderated photo now reads as an
  // internal error rather than a content rejection. Telling the two apart needs
  // the provider's machine-readable error code, and the response body is
  // deliberately drained and discarded in openrouter.provider.ts so that no
  // provider prose can reach a domain type or a log line. Of the two possible
  // mistakes, blaming the user for our bug is the worse one.
  if (status === 400 || status === 422) return 'malformed-response';

  return 'provider-unavailable';
};

interface RawCandidate {
  label?: unknown;
  confidence?: unknown;
  assumptions?: unknown;
}

const ASSUMPTION_KEYS: Readonly<Record<string, string>> = {
  'portion-unknown': 'inference.assumption.portion_unknown',
  'sauce-unknown': 'inference.assumption.sauce_unknown',
  'preparation-unknown': 'inference.assumption.preparation_unknown',
  'ingredient-hidden': 'inference.assumption.ingredient_hidden',
};

/** A non-null, non-array object — the only shape safe to read properties from. */
const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export const mapProviderResponse = (
  payload: unknown,
  profile: ModelProfile,
): Result<MappedResponse, InferenceFailure> => {
  if (payload === null || typeof payload !== 'object') return err('malformed-response');
  const root = payload as Record<string, unknown>;

  const choices = root['choices'];
  if (!Array.isArray(choices) || choices.length === 0) return err('malformed-response');

  // M-01 — a null entry here was cast and dereferenced, throwing a TypeError
  // that escaped as `provider-unavailable` and was retried.
  const first = isPlainObject(choices[0]) ? choices[0] : undefined;
  const message = first?.['message'] as Record<string, unknown> | undefined;
  const content = message?.['content'];
  if (typeof content !== 'string') return err('malformed-response');

  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch {
    return err('malformed-response');
  }

  // A model that returns nutrition has violated the prompt contract. Stripping
  // the fields would hide the violation; failing surfaces it (AD-7).
  if (containsForbiddenKey(parsed)) return err('malformed-response');

  // M-01 — JSON.parse happily returns null, a number, a string or an array,
  // and every one of those was cast to a record. `null['candidates']` throws a
  // TypeError, which the provider reported as an outage and retried: another
  // request spent on a response that will be exactly as unreadable, and a user
  // told the provider is down when it answered promptly with something we
  // could not read. Unreadable is not unavailable.
  if (!isPlainObject(parsed)) return err('malformed-response');
  const body = parsed;
  const rawCandidates = body['candidates'];
  if (!Array.isArray(rawCandidates)) return err('malformed-response');

  const modelId =
    typeof root['model'] === 'string' && root['model'].length > 0
      ? (root['model'] as string)
      : profile.model;

  const candidates: Candidate[] = [];
  for (const [index, entry] of rawCandidates.slice(0, 5).entries()) {
    if (!isPlainObject(entry)) return err('malformed-response');
    const raw = entry as RawCandidate;
    if (typeof raw.label !== 'string' || raw.label.trim().length === 0) {
      return err('malformed-response');
    }
    if (typeof raw.confidence !== 'number') return err('malformed-response');

    const score = createConfidenceScore(raw.confidence, ENGINE_ID, CALIBRATION_VERSION);
    if (!isOk(score)) return err('malformed-response');

    const assumptions = Array.isArray(raw.assumptions) ? raw.assumptions : [];
    candidates.push({
      candidateId: `cand_${String(index + 1).padStart(2, '0')}`,
      label: raw.label.trim().slice(0, 200),
      rank: index + 1,
      confidence: score.value,
      unresolvedAssumptions: assumptions
        .filter((code): code is string => typeof code === 'string' && code in ASSUMPTION_KEYS)
        .map((code) => ({ code, userMessageKey: ASSUMPTION_KEYS[code] as string })),
      provenance: {
        route: 'remote',
        engineId: ENGINE_ID,
        modelVersion: modelId,
        calibrationVersion: CALIBRATION_VERSION,
        contractVersion: 'photo-inference-engine/v1',
      },
    });
  }

  const usageRaw = root['usage'] as Record<string, unknown> | undefined;
  const usage: TokenUsage = {
    promptTokens:
      typeof usageRaw?.['prompt_tokens'] === 'number' ? usageRaw['prompt_tokens'] : null,
    completionTokens:
      typeof usageRaw?.['completion_tokens'] === 'number' ? usageRaw['completion_tokens'] : null,
  };

  void PROMPT_VERSION;
  return ok({ candidates, modelId, usage });
};
