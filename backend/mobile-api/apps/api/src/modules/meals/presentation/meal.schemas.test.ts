import { describe, it, expect } from 'vitest';
import {
  CreateManualMealRequestSchema,
  CreatePhotoMealRequestSchema,
  CreateVoiceMealRequestSchema,
  NutritionSnapshotSchema,
  ListMealsQuerySchema,
} from './meal.schemas.js';

const nutrition = {
  origin: 'client-grounded',
  catalogSource: 'usda',
  datasetVersion: 'usda-2026.1',
  servingBasis: { unit: 'g', amount: '118', gramWeight: '118' },
  calories: '105.000',
  proteinG: '1.290',
  fatG: '0.390',
  carbG: '26.950',
  fiberG: '3.070',
  sugarG: '14.430',
  sodiumMg: '1.180',
  perItem: [
    {
      foodRef: 'usda:fdc:171705',
      calories: '105.000',
      proteinG: '1.290',
      fatG: '0.390',
      carbG: '26.950',
      partial: false,
    },
  ],
};

const manual = {
  occurredAt: '2026-07-29T12:30:00Z',
  localDate: '2026-07-29',
  timezone: 'Europe/Berlin',
  mealType: 'lunch',
  items: [
    {
      foodRef: 'usda:fdc:171705',
      displayName: 'Banana, raw',
      servingUnit: 'g',
      servingAmount: '118',
      confirmedByUser: true,
    },
  ],
  nutrition,
};

describe('meal request validation', () => {
  it('accepts a well-formed manual meal', () => {
    expect(CreateManualMealRequestSchema.safeParse(manual).success).toBe(true);
  });

  // AD-7: an ungrounded number must not enter the database, and the boundary is
  // the last place to stop it (docs/06 §6.6).
  it('rejects nutrition without a catalog source', () => {
    const { catalogSource: _omitted, ...ungrounded } = nutrition;
    const result = CreateManualMealRequestSchema.safeParse({ ...manual, nutrition: ungrounded });
    expect(result.success).toBe(false);
  });

  it('rejects nutrition without a dataset version', () => {
    const { datasetVersion: _omitted, ...ungrounded } = nutrition;
    expect(NutritionSnapshotSchema.safeParse(ungrounded).success).toBe(false);
  });

  it('rejects an empty dataset version', () => {
    expect(NutritionSnapshotSchema.safeParse({ ...nutrition, datasetVersion: '' }).success).toBe(
      false,
    );
  });

  it('requires a namespace-qualified food reference', () => {
    const bad = {
      ...manual,
      items: [{ ...manual.items[0], foodRef: '171705' }],
    };
    expect(CreateManualMealRequestSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an invalid IANA timezone', () => {
    expect(
      CreateManualMealRequestSchema.safeParse({ ...manual, timezone: 'Mars/Olympus_Mons' }).success,
    ).toBe(false);
  });

  it('rejects a malformed local date', () => {
    expect(
      CreateManualMealRequestSchema.safeParse({ ...manual, localDate: '29-07-2026' }).success,
    ).toBe(false);
  });

  it('rejects an empty item list and enforces the upper bound', () => {
    expect(CreateManualMealRequestSchema.safeParse({ ...manual, items: [] }).success).toBe(false);
    const tooMany = Array.from({ length: 51 }, () => manual.items[0]);
    expect(CreateManualMealRequestSchema.safeParse({ ...manual, items: tooMany }).success).toBe(
      false,
    );
  });

  it('rejects a nutrient value with excessive precision', () => {
    expect(NutritionSnapshotSchema.safeParse({ ...nutrition, calories: '105.00001' }).success).toBe(
      false,
    );
  });

  // Contract compatibility rule: ignore unknown, reject missing required.
  it('strips unknown fields rather than rejecting them', () => {
    const parsed = CreateManualMealRequestSchema.safeParse({ ...manual, futureField: 'ignored' });
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.data).not.toHaveProperty('futureField');
  });

  it('requires an attempt id on the photo path', () => {
    const { ...photo } = { ...manual, configurationVersion: 'cfg/1' };
    expect(CreatePhotoMealRequestSchema.safeParse(photo).success).toBe(false);
    expect(
      CreatePhotoMealRequestSchema.safeParse({ ...photo, attemptId: 'att_01J8X4Q2K7R9M3P5' })
        .success,
    ).toBe(true);
  });

  it('requires a bounded transcript on the voice path', () => {
    expect(CreateVoiceMealRequestSchema.safeParse(manual).success).toBe(false);
    expect(
      CreateVoiceMealRequestSchema.safeParse({ ...manual, transcript: 'a banana' }).success,
    ).toBe(true);
    expect(
      CreateVoiceMealRequestSchema.safeParse({ ...manual, transcript: 'x'.repeat(501) }).success,
    ).toBe(false);
  });

  it('applies pagination defaults and bounds', () => {
    const parsed = ListMealsQuerySchema.safeParse({});
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.data.limit).toBe(20);
    expect(ListMealsQuerySchema.safeParse({ limit: '101' }).success).toBe(false);
    expect(ListMealsQuerySchema.safeParse({ limit: '0' }).success).toBe(false);
  });
});
