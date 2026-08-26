/**
 * Every bound is a named constant so a limit is changed in one reviewed place
 * rather than in a literal at a call site (docs/06 §6.3).
 */
export const LIMITS = {
  MAX_UPLOAD_BYTES: 8 * 1024 * 1024,
  MAX_IMAGE_DIMENSION: 4096,
  MAX_MEAL_ITEMS: 50,
  MAX_PAGE_LIMIT: 100,
  DEFAULT_PAGE_LIMIT: 20,
  MAX_TEXT_DESCRIPTION_CHARS: 500,
  /** `priorMealSummary` in contracts/mobile-api/v1/gateway-request.toml. */
  MAX_PRIOR_MEAL_SUMMARY_CHARS: 280,
  MAX_DISPLAY_NAME_CHARS: 120,
  IDEMPOTENCY_TTL_SECONDS: 86_400,
  NONCE_TTL_SECONDS: 300,
  ACCESS_TOKEN_TTL_SECONDS: 900,
  REFRESH_TOKEN_TTL_SECONDS: 2_592_000,
} as const;

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export type AllowedImageType = (typeof ALLOWED_IMAGE_TYPES)[number];
