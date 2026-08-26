import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { randomUUID } from 'node:crypto';
import { createDatabase } from '../client.js';
import { DrizzleMealEventRepository } from './meal-event.repository.js';

/**
 * M-01 — the correction race, against a REAL PostgreSQL.
 *
 * `supersede()` selects then inserts. The unique index on `supersedes_id` is
 * what actually prevents a second head; the select is only an optimisation.
 * Two transactions can both observe no successor, and the loser hits the index.
 *
 * An in-memory repository cannot produce that: it has no index, so it cannot
 * lose the race. This is the only place the mapping from unique-violation to
 * the stale outcome is proved rather than asserted against a hand-thrown error.
 *
 * SKIPPED unless `DATABASE_TEST_URL` is set, loudly. Bring one up with:
 *
 *   initdb -D /tmp/pg -U postgres --auth=trust --locale=C
 *   pg_ctl -D /tmp/pg -o "-p 55432" start
 *   createdb -h 127.0.0.1 -p 55432 -U postgres sn_test
 *   psql ... -f packages/database/src/migrations/0000_*.sql
 *   psql ... -f packages/database/src/migrations/0001_*.sql
 */

const url = process.env['DATABASE_TEST_URL'];

const userId = randomUUID();

const eventRow = (id: string, over: Record<string, unknown> = {}) => ({
  id,
  userId,
  occurredAt: new Date('2026-08-16T12:00:00Z'),
  localDate: '2026-08-16',
  timezone: 'UTC',
  source: 'manual' as const,
  mealType: 'lunch' as const,
  status: 'confirmed' as const,
  revision: 1,
  supersedesId: null,
  attemptId: null,
  configurationVersion: null,
  transcript: null,
  createdAt: new Date(),
  ...over,
});

describe.skipIf(!url)('the correction race against a real database', () => {
  let handle: { db: ReturnType<typeof createDatabase>['db']; close: () => Promise<void> };
  let repo: DrizzleMealEventRepository;

  beforeAll(async () => {
    handle = createDatabase({
      url: url!,
      maxConnections: 10,
      idleTimeoutSeconds: 5,
      connectTimeoutSeconds: 5,
    });
    repo = new DrizzleMealEventRepository(handle.db);
    await handle.db.execute(
      `insert into users (id, external_ref, created_at)
       values ('${userId}', 'race-test-${userId}', now()) on conflict do nothing` as never,
    );
  }, 30_000);

  afterAll(async () => {
    await handle?.close();
  });

  it('lets exactly one of two concurrent corrections win, and the index decides', async () => {
    const originalId = randomUUID();
    await repo.save(eventRow(originalId) as never, [], null as never);

    // Both corrections are issued before either resolves, so both can observe
    // no successor in their own transaction. Sequential calls would be decided
    // by the select and would pass even without the index.
    const attempts = [randomUUID(), randomUUID()].map((id) =>
      repo
        .supersede(
          originalId as never,
          eventRow(id, { supersedesId: originalId }) as never,
          [],
          null as never,
        )
        .then((r) => ({ ok: true as const, r }))
        .catch((e: unknown) => ({ ok: false as const, e })),
    );
    const results = await Promise.all(attempts);

    const winners = results.filter((x) => x.ok && x.r !== null);
    const losers = results.filter((x) => !x.ok || x.r === null);

    expect(winners, 'more than one correction became the new head').toHaveLength(1);
    expect(losers).toHaveLength(1);

    // The database really does hold exactly one successor.
    const heads = await handle.db.execute(
      `select count(*)::int as n from meal_events where supersedes_id = '${originalId}'` as never,
    );
    expect((heads as unknown as { n: number }[])[0]?.n).toBe(1);
  }, 30_000);

  it('reports the loser as a unique violation the adapter can classify', async () => {
    // The mapping under test lives in the adapter; what this proves is that the
    // database really raises 23505 here, which is the premise the mapping rests
    // on. Asserting the code without ever seeing the real error would be
    // assuming the thing worth checking.
    const originalId = randomUUID();
    await repo.save(eventRow(originalId) as never, [], null as never);
    await repo.supersede(
      originalId as never,
      eventRow(randomUUID(), { supersedesId: originalId }) as never,
      [],
      null as never,
    );

    // A second correction, now that a successor exists. The pre-check catches
    // this one and returns null; force past it by inserting directly.
    let code: unknown = null;
    try {
      await handle.db.execute(
        `insert into meal_events
           (id, user_id, source, status, occurred_at, local_date, timezone, meal_type,
            revision, supersedes_id, created_at)
         values ('${randomUUID()}', '${userId}', 'manual', 'confirmed', now(), '2026-08-16',
                 'UTC', 'lunch', 2, '${originalId}', now())` as never,
      );
    } catch (error) {
      // The code is on the CAUSE, not the thrown error: drizzle wraps the driver
      // failure. That is the whole reason this lane exists — the adapter's first
      // version checked only the top level, passed its unit tests, and would
      // never have fired here.
      code = (error as { cause?: { code?: unknown } }).cause?.code;
    }
    expect(code, 'the unique index did not raise 23505 on the cause').toBe('23505');
  }, 30_000);
});

describe.skipIf(!!url)('correction-race lane', () => {
  it('is skipped: set DATABASE_TEST_URL to exercise the index race', () => {
    expect(url).toBeUndefined();
  });
});
