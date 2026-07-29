// Dependency and import rules from docs/03-folder-structure.md §3.4 and
// docs/04-ddd-module-design.md §4.9. Every rule here corresponds to an
// architectural invariant that is otherwise unprovable.
//
// eslint-plugin-boundaries v7 object-selector syntax. Captured values from an
// element pattern are referenced as {{from.<capture>}}.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import boundaries from 'eslint-plugin-boundaries';

/**
 * AD-36 product boundary patterns.
 *
 * Regex rather than glob: minimatch does not match leading dot segments, so a
 * glob written against a parent-directory traversal silently allows exactly the
 * escape it is meant to block.
 *
 * Note the patterns below are assembled so this file contains no literal
 * traversal path of its own — the repository boundary validator scans for
 * path-shaped references, and it cannot tell an example in a comment from a
 * real edge.
 */
const PRODUCT_BOUNDARY_PATTERNS = [
  {
    regex: '(^|/)\\.\\./.*(^|/)mobile/',
    message:
      'AD-36: no cross-product source edge to mobile/. Consume pinned contract releases as data instead.',
  },
  {
    regex: '(^|/)web/landing/',
    message: 'AD-36: no cross-product source edge to web/landing/.',
  },
  {
    regex: '(^|/)contracts/(mobile-api|inference|interaction-policy)/',
    message:
      'AD-36: root contracts/ is not a runtime dependency. Use contracts-generated/ bindings.',
  },
];

/** Principle 4 / AD-9: provider SDKs are confined to their adapter directory. */
const PROVIDER_SDK_PATHS = [
  { name: 'openai', message: 'Principle 4: provider SDKs belong in the adapter only.' },
  { name: '@anthropic-ai/sdk', message: 'Principle 4: provider SDKs belong in the adapter only.' },
  {
    name: '@google/generative-ai',
    message: 'Principle 4: provider SDKs belong in the adapter only.',
  },
];

/** Same-module selector for a given layer, via the `module` capture group. */
const sameModule = (type) => ({ type, module: '{{from.module}}' });

export default tseslint.config(
  {
    ignores: [
      '**/dist/**',
      '**/coverage/**',
      '**/node_modules/**',
      '**/.npm-cache/**',
      'eslint.config.js',
      'vitest.config.ts',
      '**/drizzle.config.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    plugins: { boundaries },
    settings: {
      // Workspace package names and NodeNext '.js' specifiers must resolve to
      // their TypeScript sources, or the plugin classifies them as external and
      // silently enforces nothing.
      'import/resolver': {
        typescript: {
          alwaysTryTypes: true,
          noWarnOnMultipleProjects: true,
          project: [
            './apps/api/tsconfig.json',
            './packages/shared/tsconfig.json',
            './packages/contracts/tsconfig.json',
            './packages/database/tsconfig.json',
          ],
        },
      },
      'boundaries/include': ['apps/**/*.ts', 'packages/**/*.ts'],
      'boundaries/elements': [
        // Composition root — the only place concrete adapters are chosen.
        { type: 'composition-root', pattern: 'apps/api/src/main.ts', partialMatch: false },
        { type: 'composition-root', pattern: 'apps/api/src/server.ts', partialMatch: false },

        // Gateway route group. Deliberately cannot reach persistence.
        { type: 'gateway', pattern: 'apps/api/src/routes/gateway/**' },
        { type: 'product-routes', pattern: 'apps/api/src/routes/product/**' },

        { type: 'app-config', pattern: 'apps/api/src/config/**' },
        { type: 'app-logging', pattern: 'apps/api/src/logging/**' },
        { type: 'app-middleware', pattern: 'apps/api/src/middleware/**' },
        { type: 'app-security', pattern: 'apps/api/src/security/**' },

        // Module layers, captured per module so same-module rules are expressible.
        {
          type: 'module-domain',
          pattern: 'apps/api/src/modules/*/domain/**',
          capture: ['module'],
        },
        {
          type: 'module-application',
          pattern: 'apps/api/src/modules/*/application/**',
          capture: ['module'],
        },
        {
          type: 'module-infrastructure',
          pattern: 'apps/api/src/modules/*/infrastructure/**',
          capture: ['module'],
        },
        {
          type: 'module-presentation',
          pattern: 'apps/api/src/modules/*/presentation/**',
          capture: ['module'],
        },
        {
          type: 'module-contracts',
          pattern: 'apps/api/src/modules/*/contracts/**',
          capture: ['module'],
        },
        { type: 'module-barrel', pattern: 'apps/api/src/modules/*/index.ts', partialMatch: false },

        { type: 'shared', pattern: 'packages/shared/**' },
        { type: 'internal-contracts', pattern: 'packages/contracts/**' },
        { type: 'database', pattern: 'packages/database/**' },
      ],
      'boundaries/files': [{ category: 'test', pattern: '**/*.test.ts' }],
    },
    rules: {
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // --- Domain is pure: shared kernel only. -----------------------
            // No framework, no SQL, no HTTP, no Drizzle, no Redis, no provider.
            {
              from: { element: { type: 'module-domain' } },
              allow: { to: { element: { types: { anyOf: ['shared', 'module-domain'] } } } },
            },

            // --- Application: own domain + other modules' published contracts.
            {
              from: { element: { type: 'module-application' } },
              allow: {
                to: {
                  element: {
                    types: { anyOf: ['shared', 'internal-contracts', 'module-contracts'] },
                  },
                },
              },
            },
            {
              from: { element: { type: 'module-application' } },
              allow: { to: { element: sameModule('module-domain') } },
            },

            // --- Infrastructure implements its own module's ports. ---------
            {
              from: { element: { type: 'module-infrastructure' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: [
                        'shared',
                        'database',
                        'internal-contracts',
                        'app-config',
                        'app-logging',
                        'module-contracts',
                      ],
                    },
                  },
                },
              },
            },
            {
              from: { element: { type: 'module-infrastructure' } },
              allow: {
                to: {
                  element: [sameModule('module-domain'), sameModule('module-application')],
                },
              },
            },

            // --- Presentation talks to its own application layer. ----------
            {
              from: { element: { type: 'module-presentation' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: [
                        'shared',
                        'app-config',
                        'app-logging',
                        'app-middleware',
                        'internal-contracts',
                      ],
                    },
                  },
                },
              },
            },
            {
              from: { element: { type: 'module-presentation' } },
              allow: {
                to: {
                  element: [
                    sameModule('module-application'),
                    sameModule('module-domain'),
                    sameModule('module-contracts'),
                  ],
                },
              },
            },

            // --- Published boundary interfaces stay dependency-free. -------
            {
              from: { element: { type: 'module-contracts' } },
              allow: {
                to: { element: { types: { anyOf: ['shared', 'internal-contracts'] } } },
              },
            },
            {
              from: { element: { type: 'module-contracts' } },
              allow: { to: { element: sameModule('module-domain') } },
            },

            // --- Module barrel re-exports its own layers. -------------------
            {
              from: { element: { type: 'module-barrel' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: [
                        'module-domain',
                        'module-application',
                        'module-infrastructure',
                        'module-presentation',
                        'module-contracts',
                        'shared',
                      ],
                    },
                  },
                },
              },
            },

            // --- The gateway may compose auth and inference, but has NO -----
            // persistence edge. Its inability to durably store user content is
            // a dependency-graph property, not a review convention
            // (docs/09 §9.6). This disallow is the enforcement.
            {
              from: { element: { type: 'gateway' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: [
                        'shared',
                        'app-config',
                        'app-logging',
                        'app-middleware',
                        'app-security',
                        'internal-contracts',
                        'module-application',
                        'module-presentation',
                        'module-contracts',
                        'module-domain',
                        'module-barrel',
                      ],
                    },
                  },
                },
              },
            },
            {
              from: { element: { type: 'gateway' } },
              disallow: {
                to: {
                  element: { types: { anyOf: ['database', 'module-infrastructure'] } },
                },
              },
            },

            {
              from: { element: { type: 'product-routes' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: [
                        'shared',
                        'app-config',
                        'app-logging',
                        'app-middleware',
                        'app-security',
                        'internal-contracts',
                        'module-application',
                        'module-presentation',
                        'module-contracts',
                        'module-domain',
                        'module-barrel',
                      ],
                    },
                  },
                },
              },
            },

            // --- Cross-cutting packages. -----------------------------------
            {
              from: { element: { type: 'shared' } },
              allow: { to: { element: { type: 'shared' } } },
            },
            {
              from: { element: { type: 'internal-contracts' } },
              allow: {
                to: { element: { types: { anyOf: ['shared', 'internal-contracts'] } } },
              },
            },
            {
              from: { element: { type: 'database' } },
              allow: { to: { element: { types: { anyOf: ['shared', 'database'] } } } },
            },
            {
              from: { element: { type: 'app-config' } },
              allow: { to: { element: { types: { anyOf: ['shared', 'app-config'] } } } },
            },
            {
              from: { element: { type: 'app-logging' } },
              allow: {
                to: {
                  element: { types: { anyOf: ['shared', 'app-config', 'app-logging'] } },
                },
              },
            },
            {
              from: { element: { type: 'app-security' } },
              allow: {
                to: { element: { types: { anyOf: ['shared', 'app-config', 'app-security'] } } },
              },
            },
            {
              from: { element: { type: 'app-middleware' } },
              allow: {
                to: {
                  element: {
                    types: {
                      anyOf: ['shared', 'app-config', 'app-logging', 'app-middleware'],
                    },
                  },
                },
              },
            },

            // --- Composition root wires everything. Only such exception. ----
            {
              from: { element: { type: 'composition-root' } },
              allow: { to: { element: { types: '*' } } },
            },

            // --- Tests may reach their own subject and the shared kernel. ---
            {
              from: { file: { categories: 'test' } },
              allow: { to: { element: { types: '*' } } },
            },

            // Nothing may import a test file.
            { disallow: { to: { file: { categories: 'test' } } } },
          ],
        },
      ],
      'no-restricted-imports': ['error', { patterns: PRODUCT_BOUNDARY_PATTERNS }],
    },
  },
  {
    // Principle 4 / AD-9: provider SDKs are confined to their adapter directory.
    //
    // The product-boundary patterns are repeated here on purpose. A later flat
    // config block REPLACES a rule's options rather than merging them, so
    // omitting them would silently drop the AD-36 boundary for every file under
    // apps/api/src — which is most of the product.
    files: ['apps/api/src/**/*.ts'],
    ignores: ['apps/api/src/modules/inference/infrastructure/openrouter/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        { patterns: PRODUCT_BOUNDARY_PATTERNS, paths: PROVIDER_SDK_PATHS },
      ],
    },
  },
  {
    // Stage 2 declares contracts whose parameters are unused until Stage 3
    // supplies the bodies. A leading underscore marks that deliberately, so the
    // signature stays reviewable without disabling the rule outright.
    rules: {
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
          destructuredArrayIgnorePattern: '^_',
          ignoreRestSiblings: true,
        },
      ],
    },
  },
  {
    files: ['**/*.test.ts'],
    rules: { '@typescript-eslint/no-explicit-any': 'off' },
  },
);
