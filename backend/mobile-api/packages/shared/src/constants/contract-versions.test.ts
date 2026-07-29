import { describe, it, expect } from 'vitest';
import {
  MOBILE_API_CONTRACT,
  SUPPORTED_MOBILE_API_VERSIONS,
  isSupportedContractVersion,
} from './contract-versions.js';

describe('contract versions', () => {
  it('accepts every supported released version', () => {
    for (const v of SUPPORTED_MOBILE_API_VERSIONS) {
      expect(isSupportedContractVersion(v)).toBe(true);
    }
  });

  it('rejects an unreleased version', () => {
    expect(isSupportedContractVersion('mobile-api/v2')).toBe(false);
    expect(isSupportedContractVersion('')).toBe(false);
  });

  it('pins the current release', () => {
    expect(MOBILE_API_CONTRACT).toBe('mobile-api/v1');
  });
});
