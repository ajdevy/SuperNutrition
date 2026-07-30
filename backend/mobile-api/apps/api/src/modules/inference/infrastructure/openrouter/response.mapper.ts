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
  if (status === 400 || status === 422) return 'content-rejected';
  if (status >= 500) return 'provider-unavailable';
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

export const mapProviderResponse = (
  payload: unknown,
  profile: ModelProfile,
): Result<MappedResponse, InferenceFailure> => {
  if (payload === null || typeof payload !== 'object') return err('malformed-response');
  const root = payload as Record<string, unknown>;

  const choices = root['choices'];
  if (!Array.isArray(choices) || choices.length === 0) return err('malformed-response');

  const first = choices[0] as Record<string, unknown> | undefined;
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

  const body = parsed as Record<string, unknown>;
  const rawCandidates = body['candidates'];
  if (!Array.isArray(rawCandidates)) return err('malformed-response');

  const modelId =
    typeof root['model'] === 'string' && root['model'].length > 0
      ? (root['model'] as string)
      : profile.model;

  const candidates: Candidate[] = [];
  for (const [index, entry] of rawCandidates.slice(0, 5).entries()) {
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
