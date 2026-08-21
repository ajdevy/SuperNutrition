import { describe, it, expect } from 'vitest';
import { InMemoryIdempotencyStore, RedisIdempotencyStore, type RedisLike } from './stores.js';

/** The two bounds every claim now carries; the default the suite reasons about. */
const TTL = { inFlightSeconds: 60, completedSeconds: 86_400 } as const;
/** One explicit window, when a test is about expiry rather than the split. */
const ttl = (seconds: number) => ({ inFlightSeconds: seconds, completedSeconds: seconds });

/**
 * Multi-instance Product idempotency — story 4.4 criterion 3.
 *
 * Two replicas could each accept the same Idempotency-Key and create two
 * durable meal events: the exact double-write the key exists to prevent. The
 * gateway's own state was already shared; this was the last protected store
 * still living in one process, and the instance fence stayed closed for it.
 *
 * ## What is stored, and what deliberately is not
 *
 * Owner decision: Redis holds the STATUS, the response digest and a REFERENCE —
 * never the response body. The body is a meal diary entry carrying `userId`,
 * dish names, times and nutrition; putting it in Redis would place Product API
 * user content there for the first time, under retention rules written for the
 * database rather than for a coordination store.
 *
 * So a replay re-reads the meal from Postgres by its id and reproduces the same
 * response from the row that already exists. One extra read, on the rare path.
 */

/** Redis whose commands behave as the real server documents them. */
class FakeRedis implements RedisLike {
  readonly store = new Map<string, { value: string; expiresAt: number }>();
  now = 1_000_000;
  readonly written: string[] = [];

  #live(key: string) {
    const entry = this.store.get(key);
    if (!entry) return undefined;
    if (entry.expiresAt <= this.now) {
      this.store.delete(key);
      return undefined;
    }
    return entry;
  }

  // NX is HONOURED rather than assumed. The fake used to refuse any set on a
  // live key whether or not NX was passed, so an unconditional write silently
  // did nothing — and the suite read that as a conflict. A fake that is stricter
  // than the server it stands in for hides the behaviour under test.
  async set(key: string, value: string, _m: 'EX', ttl: number, nx?: 'NX') {
    if (nx === 'NX' && this.#live(key)) return null;
    this.store.set(key, { value, expiresAt: this.now + ttl * 1000 });
    this.written.push(value);
    return 'OK';
  }
  async get(key: string) {
    return this.#live(key)?.value ?? null;
  }
  async del(key: string) {
    return this.store.delete(key) ? 1 : 0;
  }
  async eval() {
    return null;
  }
}

const unreachable = (): RedisLike => ({
  eval: async () => {
    throw new Error('ECONNREFUSED');
  },
  set: async () => {
    throw new Error('ECONNREFUSED');
  },
  get: async () => {
    throw new Error('ECONNREFUSED');
  },
  del: async () => {
    throw new Error('ECONNREFUSED');
  },
});

/** A meal response, as the Product API really shapes it. */
const meal = (id: string) => ({
  status: 201,
  body: { id, userId: 'user-1', mealType: 'lunch', items: [{ displayName: 'Банан' }] },
});

/** Stands in for the database the composition root binds. */
const database = () => {
  const rows = new Map<string, unknown>();
  return {
    rows,
    describe: (response: unknown) => {
      const body = (response as { status?: number; body?: { id?: string } })?.body;
      const status = (response as { status?: number })?.status;
      if (typeof body?.id !== 'string' || typeof status !== 'number') return null;
      rows.set(body.id, response);
      return { status, ref: body.id };
    },
    rehydrate: async (ref: string, _key: string) => rows.get(ref) ?? null,
  };
};

const build = (redis: RedisLike, db = database()) =>
  new RedisIdempotencyStore(redis, { describe: db.describe, rehydrate: db.rehydrate });

const DIGEST = 'a'.repeat(64);
const OTHER = 'b'.repeat(64);

describe('the shared store matches the in-memory contract', () => {
  /**
   * Both implementations are held to the same script, because the semantics are
   * the contract clients already depend on — a divergence would surface as
   * production behaving differently from development.
   */
  const script = [
    [
      'a first claim is fresh',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) =>
        (await s.claim('k', DIGEST, ttl(60))).state,
    ],
    [
      'the same key while in flight conflicts',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60));
        return (await s.claim('k', DIGEST, ttl(60))).state;
      },
    ],
    [
      'a different body on the same key conflicts',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60));
        return (await s.claim('k', OTHER, ttl(60))).state;
      },
    ],
    [
      'a completed key replays',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60));
        await s.complete('k', meal('11111111-1111-4111-8111-111111111111'), ttl(60));
        return (await s.claim('k', DIGEST, ttl(60))).state;
      },
    ],
    [
      'a released key is fresh again',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60));
        await s.fail('k');
        return (await s.claim('k', DIGEST, ttl(60))).state;
      },
    ],
  ] as const;

  it.each(script)('%s — identically in both', async (_label, run) => {
    const db = database();
    const inMemory = await run(new InMemoryIdempotencyStore());
    const shared = await run(build(new FakeRedis(), db));
    expect(shared).toBe(inMemory);
  });
});

describe('exactly one durable write across instances', () => {
  it('lets one of two replicas claim, and conflicts the other', async () => {
    // The finding itself: per-process stores let both claim and both create.
    const redis = new FakeRedis();
    const a = build(redis);
    const b = build(redis);

    const claims = await Promise.all([
      a.claim('u:k', DIGEST, ttl(60)),
      b.claim('u:k', DIGEST, ttl(60)),
    ]);
    expect(claims.filter((c) => c.state === 'fresh')).toHaveLength(1);
    expect(claims.filter((c) => c.state === 'conflict')).toHaveLength(1);
  });

  it('replays the first replica response on the second', async () => {
    const redis = new FakeRedis();
    const db = database();
    const a = build(redis, db);
    const b = build(redis, db);
    const created = meal('22222222-2222-4222-8222-222222222222');

    await a.claim('u:k', DIGEST, ttl(60));
    await a.complete('u:k', created, ttl(60));

    const replay = await b.claim('u:k', DIGEST, ttl(60));
    expect(replay.state).toBe('replay');
    expect(replay.state === 'replay' && replay.response).toEqual(created);
  });
});

describe('no user content reaches Redis', () => {
  it('stores a status, a digest and a reference — nothing from the body', async () => {
    const redis = new FakeRedis();
    const store = build(redis);
    const created = meal('33333333-3333-4333-8333-333333333333');

    await store.claim('user-1:k', DIGEST, ttl(60));
    await store.complete('user-1:k', created, TTL);

    for (const value of redis.written) {
      expect(value).not.toContain('Банан');
      expect(value).not.toContain('user-1');
      expect(value).not.toContain('lunch');
      expect(value.length).toBeLessThan(160);
    }
  });
});

describe('honest answers when the body cannot be reproduced', () => {
  it('conflicts rather than inventing a response when the row is gone', async () => {
    // The user created a meal with this key and then deleted it. We know the key
    // was used for a successful create and cannot reproduce what was returned.
    // Conflict is the honest answer: refusing is not ideal, but replaying an
    // invented body, or creating a second meal under a key that already made
    // one, are both worse.
    const redis = new FakeRedis();
    const db = database();
    const store = build(redis, db);
    const created = meal('44444444-4444-4444-8444-444444444444');

    await store.claim('u:k', DIGEST, ttl(60));
    await store.complete('u:k', created, TTL);
    db.rows.clear(); // the meal was deleted

    expect((await store.claim('u:k', DIGEST, ttl(60))).state).toBe('conflict');
  });

  it('conflicts when the response carries no reference to re-read', async () => {
    const redis = new FakeRedis();
    const store = build(redis, database());

    await store.claim('u:k', DIGEST, ttl(60));
    await store.complete('u:k', { status: 202, body: { noIdHere: true } }, TTL);

    expect((await store.claim('u:k', DIGEST, ttl(60))).state).toBe('conflict');
  });
});

describe('an unreachable Redis fails closed', () => {
  it('raises rather than guessing on claim', async () => {
    await expect(build(unreachable()).claim('k', DIGEST, ttl(60))).rejects.toThrow(
      /redis unavailable/i,
    );
  });

  it('raises rather than silently losing the completion', async () => {
    await expect(build(unreachable()).complete('k', meal('x'), ttl(60))).rejects.toThrow(
      /redis unavailable/i,
    );
  });
});

describe('the claim expires with its window', () => {
  it('is fresh again once the TTL passes', async () => {
    const redis = new FakeRedis();
    const store = build(redis);
    await store.claim('k', DIGEST, ttl(60));
    redis.now += 61_000;
    expect((await store.claim('k', DIGEST, ttl(60))).state).toBe('fresh');
  });
});
