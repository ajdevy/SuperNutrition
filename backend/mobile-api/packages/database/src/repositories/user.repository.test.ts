import { describe, it, expect } from 'vitest';
import { DrizzleUserRepository } from './user.repository.js';
import type { Database } from '../client.js';
import type { UserRow } from '../schema/users.js';

interface Call {
  readonly op: string;
  readonly args: readonly unknown[];
}

const recordingDb = (results: readonly unknown[][]): { db: Database; calls: Call[] } => {
  const queue = results.map((rows) => rows);
  const calls: Call[] = [];

  const chain = (op: string, args: readonly unknown[]): Record<string, unknown> => {
    calls.push({ op, args });
    const builder: Record<string, unknown> = {};
    for (const method of [
      'from',
      'where',
      'limit',
      'values',
      'set',
      'returning',
      'onConflictDoUpdate',
    ]) {
      builder[method] = (...next: unknown[]) => {
        calls.push({ op: method, args: next });
        return builder;
      };
    }
    builder['then'] = (
      resolve: (value: unknown) => unknown,
      reject?: (error: unknown) => unknown,
    ) => Promise.resolve(queue.shift() ?? []).then(resolve, reject);
    return builder;
  };

  const db = {
    select: (...args: unknown[]) => chain('select', args),
    insert: (...args: unknown[]) => chain('insert', args),
    update: (...args: unknown[]) => chain('update', args),
  } as unknown as Database;

  return { db, calls };
};

const userRow = (over: Partial<UserRow> = {}): UserRow =>
  ({
    id: '3f7c1a2e-0000-4000-8000-0000000000ff',
    externalRef: 'install-1',
    status: 'active',
    createdAt: new Date('2026-07-29T12:00:00Z'),
    updatedAt: new Date('2026-07-29T12:00:00Z'),
    deletedAt: null,
    ...over,
  }) as UserRow;

describe('reading a user', () => {
  it('returns null rather than throwing when the id is unknown', async () => {
    const { db } = recordingDb([[]]);
    await expect(new DrizzleUserRepository(db).findById('nobody')).resolves.toBeNull();
  });

  it('returns the row for a known id', async () => {
    const { db } = recordingDb([[userRow()]]);
    const found = await new DrizzleUserRepository(db).findById(
      '3f7c1a2e-0000-4000-8000-0000000000ff',
    );
    expect(found?.externalRef).toBe('install-1');
  });

  it('looks an installation up by its external ref', async () => {
    const { db } = recordingDb([[userRow()]]);
    const found = await new DrizzleUserRepository(db).findByExternalRef('install-1');
    expect(found?.id).toBe('3f7c1a2e-0000-4000-8000-0000000000ff');
  });
});

describe('first-access provisioning', () => {
  // Two concurrent first requests from one installation are the normal case on
  // a mobile client; check-then-insert loses that race against its own index.
  it('upserts rather than reading and then inserting', async () => {
    const { db, calls } = recordingDb([[userRow()]]);
    await new DrizzleUserRepository(db).ensureByExternalRef('install-1');

    expect(calls.some((call) => call.op === 'select')).toBe(false);
    expect(calls.some((call) => call.op === 'onConflictDoUpdate')).toBe(true);
  });

  it('hands back the account for the installation', async () => {
    const { db } = recordingDb([[userRow()]]);
    const user = await new DrizzleUserRepository(db).ensureByExternalRef('install-1');
    expect(user.externalRef).toBe('install-1');
  });

  it('fails loudly when the upsert returns nothing', async () => {
    const { db } = recordingDb([[]]);
    await expect(new DrizzleUserRepository(db).ensureByExternalRef('install-1')).rejects.toThrow(
      /returned no row/,
    );
  });
});

describe('creating a user', () => {
  it('returns the inserted row', async () => {
    const { db } = recordingDb([[userRow()]]);
    const created = await new DrizzleUserRepository(db).create({ externalRef: 'install-1' });
    expect(created.id).toBe('3f7c1a2e-0000-4000-8000-0000000000ff');
  });

  it('fails loudly when the insert returns nothing', async () => {
    const { db } = recordingDb([[]]);
    await expect(new DrizzleUserRepository(db).create({ externalRef: 'x' })).rejects.toThrow(
      /returned no row/,
    );
  });
});

describe('erasure', () => {
  it('marks the row deleted rather than removing it, and says whether it did', async () => {
    const present = recordingDb([[{ id: 'u1' }]]);
    await expect(new DrizzleUserRepository(present.db).softDelete('u1')).resolves.toBe(true);

    const set = present.calls.find((call) => call.op === 'set')?.args[0] as {
      status?: string;
      deletedAt?: unknown;
    };
    expect(set.status).toBe('deleted');
    expect(set.deletedAt).toBeDefined();

    const absent = recordingDb([[]]);
    await expect(new DrizzleUserRepository(absent.db).softDelete('u1')).resolves.toBe(false);
  });
});

describe('an unknown installation', () => {
  it('is reported as absent rather than as an error', async () => {
    const { db } = recordingDb([[]]);
    await expect(new DrizzleUserRepository(db).findByExternalRef('nobody')).resolves.toBeNull();
  });
});
