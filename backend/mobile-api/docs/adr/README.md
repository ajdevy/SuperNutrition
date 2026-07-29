# Architecture Decision Records

Decisions made during Stage 2 skeleton generation. Stage 1's design documents in
the parent directory are the architecture; these record choices the skeleton
forced that the design did not already settle.

| ADR                                        | Decision                                              |
| ------------------------------------------ | ----------------------------------------------------- |
| [0001](0001-pinned-toolchain-tuple.md)     | Pinned Node/TypeScript toolchain tuple                |
| [0002](0002-layer-enforcement-strategy.md) | Two-layer boundary enforcement: lint plus source scan |
| [0003](0003-workspace-layout.md)           | npm workspaces inside the product root                |
| [0004](0004-skeleton-methods-throw.md)     | Skeleton methods throw rather than return empty       |
| [0005](0005-lifecycle-activation.md)       | Activation evidence for the AD-37 transition          |
