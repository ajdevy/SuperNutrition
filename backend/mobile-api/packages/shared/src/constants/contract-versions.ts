/**
 * Pinned contract releases this service supports. AD-36 requires testing
 * against every supported released version, not only the newest.
 */
export const MOBILE_API_CONTRACT = 'mobile-api/v1' as const;
export const INFERENCE_CONTRACT = 'photo-inference-engine/v1' as const;

export const SUPPORTED_MOBILE_API_VERSIONS = [MOBILE_API_CONTRACT] as const;
export type SupportedMobileApiVersion = (typeof SUPPORTED_MOBILE_API_VERSIONS)[number];

export const isSupportedContractVersion = (value: string): value is SupportedMobileApiVersion =>
  (SUPPORTED_MOBILE_API_VERSIONS as readonly string[]).includes(value);
