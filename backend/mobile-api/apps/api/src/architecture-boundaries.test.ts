import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Layer and product boundaries, enforced by scanning source text.
 *
 * `eslint-plugin-boundaries` is the fast feedback layer, but its analysis
 * depends on resolving each specifier, and a relative type-only import written
 * with a NodeNext `.js` extension is not reliably resolved. That gap is exactly
 * where a boundary would erode unnoticed, because most cross-layer references
 * in this codebase are `import type`.
 *
 * This test needs no resolver: it reads the specifier, normalizes it, and
 * classifies both ends. It is the authoritative check (AD-32 — quality controls
 * test themselves); the lint rule is the convenience.
 */

const here = dirname(fileURLToPath(import.meta.url));
const apiSrc = here;
const productRoot = resolve(here, '..', '..', '..');

type Zone =
  | 'domain'
  | 'application'
  | 'infrastructure'
  | 'presentation'
  | 'module-contracts'
  | 'module-barrel'
  | 'gateway'
  | 'product-routes'
  | 'config'
  | 'logging'
  | 'middleware'
  | 'security'
  | 'composition-root'
  | 'other';

interface Classified {
  readonly zone: Zone;
  readonly module: string | null;
}

const walk = (dir: string): string[] =>
  readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });

const sourceFiles = walk(apiSrc).filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts'));

const classify = (absPath: string): Classified => {
  const rel = relative(apiSrc, absPath).split(sep).join('/');
  const moduleMatch = /^modules\/([^/]+)\/([^/]+)/.exec(rel);
  if (moduleMatch) {
    const [, module, layer] = moduleMatch as unknown as [string, string, string];
    const zone: Zone =
      layer === 'domain'
        ? 'domain'
        : layer === 'application'
          ? 'application'
          : layer === 'infrastructure'
            ? 'infrastructure'
            : layer === 'presentation'
              ? 'presentation'
              : layer === 'contracts'
                ? 'module-contracts'
                : 'other';
    return { zone, module };
  }
  if (/^modules\/([^/]+)\/index\.ts$/.test(rel)) {
    const m = /^modules\/([^/]+)\//.exec(rel);
    return { zone: 'module-barrel', module: m ? (m[1] as string) : null };
  }
  if (rel.startsWith('routes/gateway/')) return { zone: 'gateway', module: null };
  if (rel.startsWith('routes/product/')) return { zone: 'product-routes', module: null };
  if (rel.startsWith('config/')) return { zone: 'config', module: null };
  if (rel.startsWith('logging/')) return { zone: 'logging', module: null };
  if (rel.startsWith('middleware/')) return { zone: 'middleware', module: null };
  if (rel.startsWith('security/')) return { zone: 'security', module: null };
  if (rel === 'main.ts' || rel === 'server.ts') return { zone: 'composition-root', module: null };
  return { zone: 'other', module: null };
};

const IMPORT_PATTERN = /(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g;

const specifiersOf = (file: string): string[] => {
  const source = readFileSync(file, 'utf8');
  return [...source.matchAll(IMPORT_PATTERN)].map((m) => m[1] as string);
};

/** Absolute path a relative specifier points at, with the NodeNext suffix undone. */
const resolveLocal = (file: string, specifier: string): string | null => {
  if (!specifier.startsWith('.')) return null;
  const target = resolve(dirname(file), specifier);
  return target.replace(/\.js$/, '.ts');
};

interface Edge {
  readonly file: string;
  readonly specifier: string;
  readonly from: Classified;
  readonly to: Classified | null;
}

const edges: Edge[] = sourceFiles.flatMap((file) =>
  specifiersOf(file).map((specifier) => {
    const targetPath = resolveLocal(file, specifier);
    return {
      file: relative(productRoot, file),
      specifier,
      from: classify(file),
      to: targetPath ? classify(targetPath) : null,
    };
  }),
);

const describeEdge = (e: Edge) => `${e.file} -> ${e.specifier}`;

describe('architecture boundary scan', () => {
  it('finds sources and edges to analyse', () => {
    expect(sourceFiles.length).toBeGreaterThan(40);
    expect(edges.length).toBeGreaterThan(40);
  });
});

/**
 * AD-1: domain is pure. No framework, no SQL, no HTTP, no Drizzle, no Redis,
 * no provider SDK, and no other module.
 */
describe('domain purity (AD-1)', () => {
  const domainEdges = edges.filter((e) => e.from.zone === 'domain');

  it('has domain files to check', () => {
    expect(domainEdges.length).toBeGreaterThan(0);
  });

  it('imports no infrastructure package', () => {
    const offenders = domainEdges.filter((e) =>
      /@supernutrition\/database|drizzle|postgres|ioredis|fastify|pino/.test(e.specifier),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });

  it('imports no other module', () => {
    const offenders = domainEdges.filter(
      (e) => e.to !== null && e.to.module !== null && e.to.module !== e.from.module,
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });

  it('reaches no outward layer', () => {
    const offenders = domainEdges.filter(
      (e) =>
        e.to !== null &&
        ['infrastructure', 'presentation', 'gateway', 'product-routes'].includes(e.to.zone),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});

/** No module reads another module's internals — only its published contracts. */
describe('module encapsulation (docs/02 §2.4)', () => {
  const crossModule = edges.filter(
    (e) =>
      e.from.module !== null &&
      e.to !== null &&
      e.to.module !== null &&
      e.to.module !== e.from.module,
  );

  it("never reaches another module's domain, application, or infrastructure", () => {
    const offenders = crossModule.filter((e) =>
      ['domain', 'application', 'infrastructure', 'presentation'].includes(e.to!.zone),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});

/**
 * AD-8: the gateway's inability to durably store user content is a
 * dependency-graph property, not a review convention (docs/09 §9.6).
 */
describe('gateway cannot persist (AD-8)', () => {
  const gatewayEdges = edges.filter((e) => e.from.zone === 'gateway');

  it('imports no database package, ORM, or repository', () => {
    const offenders = gatewayEdges.filter(
      (e) =>
        /@supernutrition\/database|drizzle|postgres/.test(e.specifier) ||
        /[Rr]epository/.test(e.specifier),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });

  it('imports no module infrastructure layer', () => {
    const offenders = gatewayEdges.filter((e) => e.to?.zone === 'infrastructure');
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});

/** AD-36: product roots integrate only through pinned contract releases. */
describe('product boundary (AD-36)', () => {
  it('has no source edge to another product root', () => {
    const offenders = edges.filter(
      (e) =>
        /(^|\/)\.\.\/.*(^|\/)mobile\//.test(e.specifier) ||
        /(^|\/)web\/landing\//.test(e.specifier),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });

  it('does not import root contracts at runtime', () => {
    const offenders = edges.filter((e) =>
      /(^|\/)contracts\/(mobile-api|inference|interaction-policy)\//.test(e.specifier),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});

/** Principle 4 / AD-9: provider SDKs stay inside their adapter. */
describe('provider isolation (Principle 4)', () => {
  it('names no provider SDK outside the openrouter adapter', () => {
    const offenders = edges.filter(
      (e) =>
        !e.file.includes(join('infrastructure', 'openrouter')) &&
        /^(openai|@anthropic-ai\/sdk|@google\/generative-ai)$/.test(e.specifier),
    );
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});

/** Nothing ships a dependency on a test file. */
describe('no production dependency on tests', () => {
  it('imports no .test module', () => {
    const offenders = edges.filter((e) => /\.test(\.[jt]s)?$/.test(e.specifier));
    expect(offenders.map(describeEdge)).toEqual([]);
  });
});
