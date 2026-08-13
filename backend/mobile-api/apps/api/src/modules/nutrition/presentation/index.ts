import { z } from 'zod';

export const FoodLookupParamsSchema = z.object({
  foodId: z.string().regex(/^(usda|off):[a-z0-9-]+:[A-Za-z0-9._-]+$/),
});

export const FoodLookupQuerySchema = z.object({
  country: z.string().regex(/^[A-Z]{2}$/),
});
