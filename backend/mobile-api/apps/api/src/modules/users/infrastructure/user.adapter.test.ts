import { describe, it, expect, vi } from 'vitest';
import type { DrizzleUserRepository } from '@supernutrition/database';
import { DrizzleUserRepositoryAdapter, toDomainUser } from './user.adapter.js';

/**
 * The account resolution behind every authenticated product request.
 *
 * A storage fault here must never look like "no such user", because the caller
 * turns absence into a refused session (docs/09 §9.2).
 */

const row = (over: Record<string, unknown> = {}) => ({
  id: '3f7c1a2e-0000-4000-8000-0000000000ff',
  externalRef: 'install-1',
  status: 'active',
  createdAt: new Date('2026-07-29T12:00:00Z'),
  updatedAt: new Date('2026-07-29T12:00:00Z'),
  deletedAt: null,
  ...over,
});

const rows = (over: Record<string, unknown> = {}): DrizzleUserRepository =>
  ({
    findById: vi.fn(async () => row()),
    findByExternalRef: vi.fn(async () => row()),
    ensureByExternalRef: vi.fn(async () => row()),
    create: vi.fn(async () => row()),
    softDelete: vi.fn(async () => true),
    ...over,
  }) as unknown as DrizzleUserRepository;

const failing = (method: string): DrizzleUserRepository =>
  rows({
    [method]: vi.fn(async () => {
      throw new Error('connection refused');
    }),
  });

describe('mapping a user row', () => {
  it('carries timestamps out as instants', () => {
    expect(toDomainUser(row() as never)).toEqual({
      id: '3f7c1a2e-0000-4000-8000-0000000000ff',
      externalRef: 'install-1',
      status: 'active',
      createdAt: '2026-07-29T12:00:00.000Z',
      deletedAt: null,
    });
  });

  it('keeps an erasure timestamp rather than flattening it to a flag', () => {
    const user = toDomainUser(row({ deletedAt: new Date('2026-07-30T09:00:00Z') }) as never);
    expect(user.deletedAt).toBe('2026-07-30T09:00:00.000Z');
  });
});

describe('reading a user', () => {
  it('returns the account for a known id', async () => {
    const found = await new DrizzleUserRepositoryAdapter(rows()).findById('u1' as never);
    expect(found.ok && found.value.externalRef).toBe('install-1');
  });

  it('reports an unknown id as not-found', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(rows({ findById: vi.fn(async () => null) }));
    expect(await adapter.findById('u1' as never)).toEqual({ ok: false, error: 'not-found' });
  });

  it('reports a storage fault as a storage fault', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(failing('findById'));
    expect(await adapter.findById('u1' as never)).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });

  it('finds an account by the installation that owns it', async () => {
    const found = await new DrizzleUserRepositoryAdapter(rows()).findByExternalRef('install-1');
    expect(found.ok && found.value.id).toBe('3f7c1a2e-0000-4000-8000-0000000000ff');
  });

  it('reports an unknown installation as not-found', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(
      rows({ findByExternalRef: vi.fn(async () => null) }),
    );
    expect(await adapter.findByExternalRef('nobody')).toEqual({ ok: false, error: 'not-found' });
  });

  it('reports a storage fault on the external-ref read', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(failing('findByExternalRef'));
    expect(await adapter.findByExternalRef('install-1')).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });
});

describe('first-access provisioning', () => {
  it('returns the account for the installation', async () => {
    const ensured = await new DrizzleUserRepositoryAdapter(rows()).ensureByExternalRef('install-1');
    expect(ensured.ok && ensured.value.externalRef).toBe('install-1');
  });

  // The unique external ref survives erasure, so the upsert can hand back a
  // deleted account. An erased user does not come back by presenting the same
  // installation id.
  it('refuses to resurrect an erased account', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(
      rows({
        ensureByExternalRef: vi.fn(async () =>
          row({ deletedAt: new Date('2026-07-30T09:00:00Z') }),
        ),
      }),
    );
    expect(await adapter.ensureByExternalRef('install-1')).toEqual({
      ok: false,
      error: 'not-found',
    });
  });

  it('never reports a storage fault as an absent account', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(failing('ensureByExternalRef'));
    expect(await adapter.ensureByExternalRef('install-1')).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });
});

describe('creating and erasing', () => {
  it('creates an account from the domain user', async () => {
    const repository = rows();
    const created = await new DrizzleUserRepositoryAdapter(repository).create({
      id: 'u1' as never,
      externalRef: 'install-1',
      status: 'active',
      createdAt: '2026-07-29T12:00:00.000Z' as never,
      deletedAt: null,
    });

    expect(created.ok).toBe(true);
    expect(repository.create).toHaveBeenCalledWith({
      id: 'u1',
      externalRef: 'install-1',
      status: 'active',
    });
  });

  it('reports a failed create as a storage fault', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(failing('create'));
    const created = await adapter.create({
      id: 'u1' as never,
      externalRef: 'install-1',
      status: 'active',
      createdAt: '2026-07-29T12:00:00.000Z' as never,
      deletedAt: null,
    });
    expect(created).toEqual({ ok: false, error: 'storage-unavailable' });
  });

  it('erases an account', async () => {
    expect(await new DrizzleUserRepositoryAdapter(rows()).softDelete('u1' as never)).toEqual({
      ok: true,
      value: undefined,
    });
  });

  it('reports erasing an absent account as not-found', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(
      rows({ softDelete: vi.fn(async () => false) }),
    );
    expect(await adapter.softDelete('u1' as never)).toEqual({ ok: false, error: 'not-found' });
  });

  it('reports a failed erasure as a storage fault, never as success', async () => {
    const adapter = new DrizzleUserRepositoryAdapter(failing('softDelete'));
    expect(await adapter.softDelete('u1' as never)).toEqual({
      ok: false,
      error: 'storage-unavailable',
    });
  });
});
