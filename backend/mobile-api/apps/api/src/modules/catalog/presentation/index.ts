import { z } from 'zod';
import { LIMITS } from '@supernutrition/shared';

export const CatalogSearchQuerySchema = z.object({
  q: z.string().trim().min(1).max(120),
  country: z.string().regex(/^[A-Z]{2}$/),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

export const CatalogPackageQuerySchema = z.object({
  country: z.string().regex(/^[A-Z]{2}$/),
});

void LIMITS;
