#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
mobile_identity="${SUPERNUTRITION_MOBILE_IDENTITY:-$repo_root/mobile/config/product-identity.toml}"
identity_contract="${SUPERNUTRITION_IDENTITY_CONTRACT:-$repo_root/contracts/product-identity/v1/metadata.toml}"
MOBILE_IDENTITY="$mobile_identity" \
IDENTITY_CONTRACT="$identity_contract" \
python3 - <<'PY'
import os
import re
import sys
import tomllib
from pathlib import Path

mobile_path = Path(os.environ["MOBILE_IDENTITY"])
contract_path = Path(os.environ["IDENTITY_CONTRACT"])
mobile = tomllib.loads(mobile_path.read_text(encoding="utf-8"))
contract = tomllib.loads(contract_path.read_text(encoding="utf-8"))

def fail(message: str) -> None:
    print(f"product-identity: {message}", file=sys.stderr)
    raise SystemExit(1)

if contract.get("source_manifest") != "mobile/config/product-identity.toml":
    fail("identity contract must bind the mobile source manifest")

visible_keys = (
    "canonical_name",
    "installed_display_name",
    "launcher_name",
    "in_app_name",
    "accessibility_name",
)
for key in visible_keys:
    if mobile.get(key) != "SuperNutrition" or contract.get(key) != "SuperNutrition":
        fail(f"visible identity mismatch for {key}")

if mobile.get("store_listing_title") != "SuperNutrition AI nutritionist":
    fail("mobile store title mismatch")
for store in ("apple", "google"):
    if contract.get("store", {}).get(store, {}).get("listing_title") != "SuperNutrition AI nutritionist":
        fail(f"{store} store title mismatch")

web = contract.get("web_landing", {})
if any(web.get(key) != "SuperNutrition" for key in ("display_name", "meta_title", "accessibility_name")):
    fail("web landing identity mismatch")

technical = contract.get("technical", {})
for key in (
    "android_application_id",
    "ios_bundle_identifier",
    "acceptance_android_application_id",
    "acceptance_ios_bundle_identifier",
):
    if technical.get(key) != mobile.get(key):
        fail(f"technical identity mismatch for {key}")
if len(set(technical.values())) != len(technical):
    fail("technical and acceptance identifiers must remain distinct")

if "SuperNutrition AI nutritionist" in [contract.get(key) for key in visible_keys]:
    fail("store-only descriptor leaked into visible identity")

repo_root = mobile_path.parents[2]
android_build = (repo_root / "mobile/apps/androidApp/build.gradle.kts").read_text(encoding="utf-8")
if not re.search(r'(?m)^\s*applicationId\s*=\s*"com\.supernutrition\.android"\s*$', android_build):
    fail("Android host applicationId does not match the identity manifest")
if not re.search(r'(?m)^\s*applicationIdSuffix\s*=\s*"\.uiAcceptance"\s*$', android_build):
    fail("Android acceptance applicationId suffix is missing or drifted")
android_strings = (repo_root / "mobile/apps/androidApp/src/main/res/values/strings.xml").read_text(encoding="utf-8")
if not re.search(r'<string\s+name="app_name">SuperNutrition</string>', android_strings):
    fail("Android visible label does not match the identity manifest")

ios_info = (repo_root / "mobile/apps/iosApp/Info.plist").read_text(encoding="utf-8")
if not re.search(r'<key>CFBundleDisplayName</key>\s*<string>SuperNutrition</string>', ios_info):
    fail("iOS display name does not match the identity manifest")
ios_project = (repo_root / "mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
if ios_project.count('PRODUCT_BUNDLE_IDENTIFIER = "com.supernutrition.ios.uiacceptance"') < 2:
    fail("iOS acceptance bundle identifier is missing from all host configurations")
if 'PRODUCT_NAME = "SuperNutrition"' not in ios_project:
    fail("iOS acceptance product name does not match the identity manifest")

for relative in ("web/landing/product-lifecycle.toml", "backend/mobile-api/product-lifecycle.toml"):
    lifecycle = tomllib.loads((repo_root / relative).read_text(encoding="utf-8"))
    if lifecycle.get("lifecycle") != "preselection" or lifecycle.get("quality_claim") is not False:
        fail(f"{relative} must remain a non-claiming preselection root")

print("product-identity: OK (mobile, web landing, store, and technical identifiers agree)")
PY
