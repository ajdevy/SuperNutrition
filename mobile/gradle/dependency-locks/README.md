# Mobile dependency locks

This directory is the owned lock-state boundary for the mobile product. Lock
files are generated only by an explicit dependency-policy change and are never
written by CI or by `qualityCheckJvmAndroid`/`qualityCheckApple`.

The checked-in version catalog is authoritative. Dynamic, snapshot, direct-file,
direct-URL, project-repository, and cross-product dependencies are rejected by
the mobile policy plugin. Both product modules and the included `build-logic`
build apply `lockAllConfigurations()`; `verifyMobilePolicy` fails when a real
Gradle subproject has no non-empty `gradle.lockfile` or when the root
`settings-gradle.lockfile` is absent.

Refresh is explicit and local-only:

```bash
./mobile/gradlew -p mobile --no-configuration-cache --write-locks \
  :apps:androidApp:dependencies :apps:sharedKit:dependencies \
  :core:config:dependencies :core:ui:dependencies \
  :domain:nutrition:dependencies :domain:photo-analysis:dependencies \
  :feature:photo-analysis:dependencies \
  :feature:photo-analysis-presenter:dependencies \
  :feature:photo-analysis-ui:dependencies \
  :feature:meal-search-presenter:dependencies \
  :platform:photo-android:dependencies :platform:photo-ios:dependencies \
  :testing:compose-support:dependencies :testing:scaffold-probe:dependencies
```

CI uses strict dependency verification and never writes locks.
