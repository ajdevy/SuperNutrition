#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$repo_root/scripts/verify-product-identity.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-identity.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

expect_rejected() {
  local name="$1"
  local fixture_repo="$2"
  local contract="$fixture_repo/contracts/product-identity/v1/metadata.toml"
  local mobile_identity="$fixture_repo/mobile/config/product-identity.toml"
  if SUPERNUTRITION_REPO_ROOT="$fixture_repo" \
    SUPERNUTRITION_MOBILE_IDENTITY="$mobile_identity" \
    SUPERNUTRITION_IDENTITY_CONTRACT="$contract" \
    "$validator" >"$fixture_root/$name.out" 2>&1; then
    printf 'identity fixture unexpectedly accepted: %s\n' "$name" >&2
    cat "$fixture_root/$name.out" >&2
    exit 1
  fi
}

refresh_digest() {
  local release_root="$1"
  python3 - "$release_root" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
compatibility = root / "compatibility.toml"
compatibility_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|<release-digest>)"\s*$',
    b'digest = "<release-digest>"',
    compatibility.read_bytes(),
)
digest = sha256()
for relative, content in sorted(
    (
        ("compatibility.toml", compatibility_bytes),
        ("fixtures/identities.toml", (root / "fixtures/identities.toml").read_bytes()),
        ("metadata.toml", (root / "metadata.toml").read_bytes()),
        ("RELEASE.md", (root / "RELEASE.md").read_bytes()),
        ("schema.toml", (root / "schema.toml").read_bytes()),
    )
):
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(content)
path = compatibility
path.write_text(
    re.sub(
        r'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|<release-digest>)"\s*$',
        f'digest = "{digest.hexdigest()}"',
        path.read_text(encoding="utf-8"),
    ),
    encoding="utf-8",
)
PY
}

prepare_fixture() {
  local name="$1"
  local fixture_repo="$fixture_root/$name"
  mkdir -p "$fixture_repo/contracts/product-identity" "$fixture_repo/mobile/config"
  cp -R "$repo_root/contracts/product-identity/v1" "$fixture_repo/contracts/product-identity/"
  mkdir -p "$fixture_repo/mobile/apps/androidApp" "$fixture_repo/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj"
  cp "$repo_root/mobile/config/product-identity.toml" "$fixture_repo/mobile/config/product-identity.toml"
  cp "$repo_root/mobile/apps/androidApp/build.gradle.kts" "$fixture_repo/mobile/apps/androidApp/build.gradle.kts"
  cp -R "$repo_root/mobile/apps/androidApp/src" "$fixture_repo/mobile/apps/androidApp/"
  cp "$repo_root/mobile/apps/iosApp/Info.plist" "$fixture_repo/mobile/apps/iosApp/Info.plist"
  cp "$repo_root/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj" \
    "$fixture_repo/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj"
  mkdir -p "$fixture_repo/web/landing" "$fixture_repo/backend/mobile-api"
  cp "$repo_root/web/landing/product-lifecycle.toml" "$fixture_repo/web/landing/product-lifecycle.toml"
  cp "$repo_root/backend/mobile-api/product-lifecycle.toml" "$fixture_repo/backend/mobile-api/product-lifecycle.toml"
  printf '%s\n' "$fixture_repo"
}

wrong_store_title="$(prepare_fixture wrong-store-title)"
python3 - "$wrong_store_title/contracts/product-identity/v1/metadata.toml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("SuperNutrition AI nutritionist", "SuperNutrition", 1), encoding="utf-8")
PY
refresh_digest "$wrong_store_title/contracts/product-identity/v1"
expect_rejected wrong-store-title "$wrong_store_title"

visible_store_leak="$(prepare_fixture visible-store-leak)"
python3 - "$visible_store_leak/contracts/product-identity/v1/metadata.toml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace('in_app_name = "SuperNutrition"', 'in_app_name = "SuperNutrition AI nutritionist"', 1), encoding="utf-8")
PY
refresh_digest "$visible_store_leak/contracts/product-identity/v1"
expect_rejected visible-store-leak "$visible_store_leak"

android_visible_variant="$(prepare_fixture android-visible-variant)"
python3 - "$android_visible_variant/mobile/apps/androidApp/src/main/res/values/strings.xml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
path.write_text(
    path.read_text(encoding="utf-8").replace("SuperNutrition", "SuperNutrition AI nutritionist", 1),
    encoding="utf-8",
)
PY
expect_rejected android-visible-variant "$android_visible_variant"

ios_visible_variant="$(prepare_fixture ios-visible-variant)"
python3 - "$ios_visible_variant/mobile/apps/iosApp/Info.plist" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
path.write_text(
    path.read_text(encoding="utf-8").replace("<string>SuperNutrition</string>", "<string>AI nutritionist</string>", 1),
    encoding="utf-8",
)
PY
expect_rejected ios-visible-variant "$ios_visible_variant"

ios_product_collision="$(prepare_fixture ios-product-collision)"
python3 - "$ios_product_collision/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(
    text.replace(
        'PRODUCT_BUNDLE_IDENTIFIER = "com.supernutrition.ios.uiacceptance"; PRODUCT_NAME = "SuperNutritionUIAcceptance"',
        'PRODUCT_BUNDLE_IDENTIFIER = "com.supernutrition.ios.uiacceptance"; PRODUCT_NAME = "SuperNutrition"',
    ),
    encoding="utf-8",
)
PY
expect_rejected ios-product-collision "$ios_product_collision"

printf '%s\n' 'product-identity negative fixtures: OK'
