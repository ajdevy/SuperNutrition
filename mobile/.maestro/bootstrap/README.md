# Bootstrap UI acceptance profile

This directory owns one cross-platform, non-product launch/DI smoke flow for
the `bootstrap` evidence profile. It is intentionally not a UJ-1 through UJ-4
journey and does not prove camera, inference, nutrition, privacy-route,
release, store, or physical-device behavior.

The flow is executable only after the separately owned Android/iOS acceptance
hosts expose `APP_ID` and the `bootstrap_shell_launch_di` semantic marker. The
flow uses Maestro CLI 2.7.0 and a JavaScript locator dictionary; the dictionary
contains no actions or assertions.

CI downloads the pinned `maestro.zip` asset and verifies it against
`mobile/.maestro/maestro-cli-2.7.0.sha256` before running either platform flow.
