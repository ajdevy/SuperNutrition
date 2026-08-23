import { describe, it, expect } from 'vitest';
import { InMemoryIdempotencyStore, RedisIdempotencyStore, type RedisLike } from './stores.js';

/** The two bounds every claim now carries; the default the suite reasons about. */
const TTL = { inFlightSeconds: 60, completedSeconds: 86_400 } as const;
/** One explicit window, when a test is about expiry rather than the split. */
const ttl = (seconds: number) => ({ inFlightSeconds: seconds, completedSeconds: seconds });

/**
 * Claim, and hand back the token the claim minted.
 *
 * `complete`/`fail` refuse to touch a record that is not theirs (DS-F19), so a
 * test that discards the token is testing the refusal rather than the behaviour
 * it means to.
 */
const claimToken = async (
  store: { claim: (...args: never[]) => Promise<{ state: string; token?: string }> },
  ...args: unknown[]
): Promise<string> => {
  const claim = await store.claim(...(args as never[]));
  if (claim.state !== 'fresh' || claim.token === undefined) {
    throw new Error(`expected a fresh claim, got ${claim.state}`);
  }
  return claim.token;
};

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
  /**
   * The two claim-token scripts, implemented rather than stubbed.
   *
   * This returned `null` for every script, so `complete` and `fail` did nothing
   * at all and the suite read a completed key as never completed. A fake that
   * silently no-ops the operation under test is the third time in this file's
   * history that a stand-in hid the behaviour it stood in for — the NX bug and
   * the ignored-script bug were the first two.
   */
  async eval(script: string, _numKeys: number, ...args: (string | number)[]) {
    const tokenOf = (value: string): string | undefined => value.split('|')[3];

    if (script.includes("redis.call('DEL', KEYS[1])")) {
      const [key, token] = args as [string, string];
      const entry = this.#live(key);
      if (!entry || tokenOf(entry.value) !== token) return 0;
      this.store.delete(key);
      return 1;
    }

    if (script.includes("redis.call('SET', KEYS[1], ARGV[1]")) {
      const [key, value, token, ttl] = args as [string, string, string, number];
      const entry = this.#live(key);
      // Absent: restore. Present and ours: promote. Present and someone else's:
      // leave it alone.
      if (entry && tokenOf(entry.value) !== token) return 0;
      this.store.set(key, { value, expiresAt: this.now + Number(ttl) * 1000 });
      this.written.push(value);
      return 1;
    }

    throw new Error(`FakeRedis has no implementation for this script: ${script.slice(0, 60)}`);
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
    // The BODY only: the status comes from the stored record, which is what
    // makes a completed 200 replay as a 200 rather than as an invented 201.
    rehydrate: async (ref: string, _owner: string) => {
      const stored = rows.get(ref) as { body?: unknown } | undefined;
      return stored?.body ?? null;
    },
  };
};

const build = (redis: RedisLike, db = database()) =>
  new RedisIdempotencyStore(redis, { describe: db.describe, rehydrate: db.rehydrate });

const OWNER = 'owner-1';
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
        (await s.claim('k', DIGEST, ttl(60), OWNER)).state,
    ],
    [
      'the same key while in flight conflicts',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60), OWNER);
        return (await s.claim('k', DIGEST, ttl(60), OWNER)).state;
      },
    ],
    [
      'a different body on the same key conflicts',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        await s.claim('k', DIGEST, ttl(60), OWNER);
        return (await s.claim('k', OTHER, ttl(60), OWNER)).state;
      },
    ],
    [
      'a completed key replays',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        const token_complete = await claimToken(s, 'k', DIGEST, ttl(60), OWNER);
        await s.complete(
          'k',
          meal('11111111-1111-4111-8111-111111111111'),
          ttl(60),
          token_complete,
        );
        return (await s.claim('k', DIGEST, ttl(60), OWNER)).state;
      },
    ],
    [
      'a released key is fresh again',
      async (s: InMemoryIdempotencyStore | RedisIdempotencyStore) => {
        const token_fail = await claimToken(s, 'k', DIGEST, ttl(60), OWNER);
        await s.fail('k', token_fail);
        return (await s.claim('k', DIGEST, ttl(60), OWNER)).state;
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
      a.claim('u:k', DIGEST, ttl(60), OWNER),
      b.claim('u:k', DIGEST, ttl(60), OWNER),
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

    const token_complete = await claimToken(a, 'u:k', DIGEST, ttl(60), OWNER);
    await a.complete('u:k', created, ttl(60), token_complete);

    const replay = await b.claim('u:k', DIGEST, ttl(60), OWNER);
    expect(replay.state).toBe('replay');
    expect(replay.state === 'replay' && replay.response).toEqual(created);
  });
});

describe('no user content reaches Redis', () => {
  it('stores a status, a digest and a reference — nothing from the body', async () => {
    const redis = new FakeRedis();
    const store = build(redis);
    const created = meal('33333333-3333-4333-8333-333333333333');

    const token_complete = await claimToken(store, 'user-1:k', DIGEST, ttl(60), OWNER);
    await store.complete('user-1:k', created, TTL, token_complete);

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

    const token_complete = await claimToken(store, 'u:k', DIGEST, ttl(60), OWNER);
    await store.complete('u:k', created, TTL, token_complete);
    db.rows.clear(); // the meal was deleted

    expect((await store.claim('u:k', DIGEST, ttl(60), OWNER)).state).toBe('conflict');
  });

  it('conflicts when the response carries no reference to re-read', async () => {
    const redis = new FakeRedis();
    const store = build(redis, database());

    const token_complete = await claimToken(store, 'u:k', DIGEST, ttl(60), OWNER);
    await store.complete('u:k', { status: 202, body: { noIdHere: true } }, TTL, token_complete);

    expect((await store.claim('u:k', DIGEST, ttl(60), OWNER)).state).toBe('conflict');
  });
});

describe('an unreachable Redis fails closed', () => {
  it('raises rather than guessing on claim', async () => {
    await expect(build(unreachable()).claim('k', DIGEST, ttl(60), OWNER)).rejects.toThrow(
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
    await store.claim('k', DIGEST, ttl(60), OWNER);
    redis.now += 61_000;
    expect((await store.claim('k', DIGEST, ttl(60), OWNER)).state).toBe('fresh');
  });
});

describe('DS-F19 — a finished request must not clobber its successor', () => {
  it('does not release a claim that is no longer its own', async () => {
    // Introduced by the DS-F2 TTL split. With a 60s in-flight bound, a create()
    // still running past it lets a same-key retry claim fresh — and the original
    // request's fail() then DELETEs the SUCCESSOR's live marker, because the
    // delete was unconditional. The next retry claims fresh again and a second
    // meal becomes possible: the duplicate window DS-F1 closed, reopened by the
    // fix for DS-F2.
    const redis = new FakeRedis();
    const store = build(redis);

    const first = await store.claim('k', DIGEST, ttl(60), OWNER);
    expect(first.state).toBe('fresh');

    // The first request is still running when its in-flight bound lapses.
    redis.now += 61_000;

    const successor = await store.claim('k', DIGEST, ttl(60), OWNER);
    expect(successor.state, 'the successor could not claim the freed key').toBe('fresh');

    // The first request finally fails and releases — but the claim it is
    // releasing belongs to somebody else now.
    await store.fail('k', first.state === 'fresh' ? first.token : '');

    expect(
      (await store.claim('k', DIGEST, ttl(60), OWNER)).state,
      'the successor lost its claim to a predecessor that had already lost the race',
    ).toBe('conflict');
  });

  it('does not overwrite a successor claim when completing late', async () => {
    // The same crossing through complete(): its SET is unconditional, so a
    // predecessor finishing after its bound lapsed stamps a `done` record over
    // the successor's in-flight marker — mid-create, for a row the successor is
    // still writing.
    const redis = new FakeRedis();
    const db = database();
    const store = build(redis, db);

    const first = await store.claim('k', DIGEST, ttl(60), OWNER);
    redis.now += 61_000;
    const successor = await store.claim('k', DIGEST, ttl(60), OWNER);
    expect(successor.state).toBe('fresh');

    await store.complete(
      'k',
      meal('44444444-4444-4444-8444-444444444444'),
      ttl(60),
      first.state === 'fresh' ? first.token : '',
    );

    expect(
      (await store.claim('k', DIGEST, ttl(60), OWNER)).state,
      'a predecessor stamped its result over the successor still creating',
    ).toBe('conflict');
  });
});
