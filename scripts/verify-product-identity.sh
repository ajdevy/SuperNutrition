#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
mobile_identity="${SUPERNUTRITION_MOBILE_IDENTITY:-$repo_root/mobile/config/product-identity.toml}"
identity_contract="${SUPERNUTRITION_IDENTITY_CONTRACT:-$repo_root/contracts/product-identity/v1/metadata.toml}"
RELEASE_DIGEST_LIB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib" \
MOBILE_IDENTITY="$mobile_identity" \
IDENTITY_CONTRACT="$identity_contract" \
python3 - <<'PY'
import os
import re
import sys
import tomllib
from pathlib import Path

sys.path.insert(0, str(Path(os.environ["RELEASE_DIGEST_LIB"])))
import release_digest as release_digest_module  # noqa: E402

mobile_path = Path(os.environ["MOBILE_IDENTITY"])
contract_path = Path(os.environ["IDENTITY_CONTRACT"])
contract_root = contract_path.parent
schema_path = contract_root / "schema.toml"
compatibility_path = contract_root / "compatibility.toml"
fixture_path = contract_root / "fixtures" / "identities.toml"
release_path = contract_root / "RELEASE.md"
for required_path in (schema_path, compatibility_path, fixture_path, release_path):
    if not required_path.is_file():
        print(f"product-identity: missing release file {required_path}", file=sys.stderr)
        raise SystemExit(1)
mobile = tomllib.loads(mobile_path.read_text(encoding="utf-8"))
contract = tomllib.loads(contract_path.read_text(encoding="utf-8"))
schema = tomllib.loads(schema_path.read_text(encoding="utf-8"))
compatibility = tomllib.loads(compatibility_path.read_text(encoding="utf-8"))
fixtures = tomllib.loads(fixture_path.read_text(encoding="utf-8"))

def fail(message: str) -> None:
    print(f"product-identity: {message}", file=sys.stderr)
    raise SystemExit(1)

if contract.get("source_manifest") != "mobile/config/product-identity.toml":
    fail("identity contract must bind the mobile source manifest")
if schema.get("contract_id") != "product-identity" or schema.get("release") != "v1":
    fail("product-identity schema release is invalid")
visible_keys = (
    "canonical_name",
    "installed_display_name",
    "launcher_name",
    "in_app_name",
    "accessibility_name",
)
field_schema = schema.get("fields", {})
expected_field_values = {
    "canonical_name": "SuperNutrition",
    "installed_display_name": "Super Nutrition",
    "launcher_name": "Super Nutrition",
    "in_app_name": "Super Nutrition",
    "accessibility_name": "Super Nutrition",
    "store_listing_title": "SuperNutrition AI nutritionist",
    "android_application_id": "com.supernutrition.android",
    "ios_bundle_identifier": "com.supernutrition.ios",
    "acceptance_android_application_id": "com.supernutrition.android.uiAcceptance",
    "acceptance_ios_bundle_identifier": "com.supernutrition.ios.uiacceptance",
}
if set(field_schema) != set(expected_field_values):
    fail("product-identity schema fields are incomplete or unexpected")
for key, expected_value in expected_field_values.items():
    definition = field_schema.get(key)
    if definition.get("type") != "string" or definition.get("allowed_values") != [expected_value]:
        fail(f"product-identity schema constraint is invalid for {key}")
schema_values = {
    **{key: contract.get(key) for key in visible_keys},
    "store_listing_title": contract.get("store", {}).get("apple", {}).get("listing_title"),
    **contract.get("technical", {}),
}
for key, value in schema_values.items():
    if value not in field_schema[key]["allowed_values"]:
        fail(f"product-identity schema rejected contract value for {key}")
if compatibility.get("contract_id") != "product-identity" or compatibility.get("release") != "v1":
    fail("product-identity compatibility metadata is invalid")
if compatibility.get("source_manifest") != "mobile/config/product-identity.toml":
    fail("product-identity compatibility must bind the mobile source manifest")
if fixtures.get("contract_release") != "product-identity/v1":
    fail("product-identity fixture release is invalid")
required_fixture_ids = {
    "canonical-visible-name",
    "store-only-title",
    "production-android-id",
    "production-ios-id",
}
if {case.get("id") for case in fixtures.get("case", [])} != required_fixture_ids:
    fail("product-identity fixture inventory drifted")

# Computed by scripts/lib/release_digest.py, not re-derived here.
#
# This file carried its own copy of the algorithm: the same sort, the same
# `path + NUL + content` loop, and its own hardcoded member list. That is the
# duplication release_digest.py was written to end — two implementations of one
# number drift, and the one that drifts is the one nobody is looking at. It also
# meant this check could not follow the release onto a newer algorithm, and its
# hardcoded five-member list would have silently ignored a sixth file.
computed_digest = release_digest_module.compute(contract_root)
if compatibility.get("digest") != computed_digest:

    fail("product-identity compatibility digest is stale")

expected_visible_values = {
    "canonical_name": "SuperNutrition",
    "installed_display_name": "Super Nutrition",
    "launcher_name": "Super Nutrition",
    "in_app_name": "Super Nutrition",
    "accessibility_name": "Super Nutrition",
}
for key in visible_keys:
    if mobile.get(key) != expected_visible_values[key] or contract.get(key) != expected_visible_values[key]:
        fail(f"visible identity mismatch for {key}")
forbidden = mobile.get("forbidden_visible_variants", {})
if set(forbidden.get("values", [])) != {"SuperNutrition AI nutritionist", "AI nutritionist"}:
    fail("mobile forbidden visible variants drifted")
if set(forbidden.get("applies_to", [])) != set(visible_keys[1:]):
    fail("mobile forbidden visible variants do not cover visible identity fields")

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

repo_root = Path(os.environ.get("SUPERNUTRITION_REPO_ROOT", str(mobile_path.parents[2])))
android_build = (repo_root / "mobile/apps/androidApp/build.gradle.kts").read_text(encoding="utf-8")
if not re.search(r'(?m)^\s*applicationId\s*=\s*"com\.supernutrition\.android"\s*$', android_build):
    fail("Android host applicationId does not match the identity manifest")
if not re.search(r'create\("production"\)\s*\{\s*dimension\s*=\s*"distribution"', android_build, re.S):
    fail("Android production flavor is missing")
if not re.search(r'(?m)^\s*applicationIdSuffix\s*=\s*"\.uiAcceptance"\s*$', android_build):
    fail("Android acceptance applicationId suffix is missing or drifted")
android_strings_root = repo_root / "mobile/apps/androidApp/src"
android_string_files = sorted(android_strings_root.rglob("strings.xml"))
if not android_string_files:
    fail("Android string resources are missing")
for path in android_string_files:
    text = path.read_text(encoding="utf-8")
    if any(value in text for value in forbidden.get("values", [])):
        fail(f"forbidden visible variant found in Android resource {path.relative_to(repo_root)}")
android_strings = (repo_root / "mobile/apps/androidApp/src/main/res/values/strings.xml").read_text(encoding="utf-8")
if not re.search(r'<string\s+name="app_name"\s*>Super Nutrition</string>', android_strings):
    fail("Android visible label does not match the identity manifest")

ios_root = repo_root / "mobile/apps/iosApp"
ios_identity_files = sorted(
    path for path in ios_root.rglob("*")
    if path.is_file() and path.suffix in {".plist", ".strings"} and "build" not in path.parts
)
if not ios_identity_files:
    fail("iOS identity resources are missing")
for path in ios_identity_files:
    text = path.read_text(encoding="utf-8")
    if any(value in text for value in forbidden.get("values", [])):
        fail(f"forbidden visible variant found in iOS resource {path.relative_to(repo_root)}")
ios_info = (repo_root / "mobile/apps/iosApp/Info.plist").read_text(encoding="utf-8")
if not re.search(r'<key>CFBundleDisplayName</key>\s*<string>Super Nutrition</string>', ios_info):
    fail("iOS display name does not match the identity manifest")
ios_project = (repo_root / "mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
# Xcode's generated pbxproj permits these scalar values both quoted and
# unquoted. The pattern is intentionally bounded to the exact bundle IDs and
# rejects semicolons/newlines inside PRODUCT_NAME rather than parsing arbitrary
# project text as identity evidence.
acceptance_product_names = set(re.findall(
    r'PRODUCT_BUNDLE_IDENTIFIER = "?com\.supernutrition\.ios\.uiacceptance"?;\s*PRODUCT_NAME = "?([^";\n]+)"?',
    ios_project,
))
production_product_names = set(re.findall(
    r'PRODUCT_BUNDLE_IDENTIFIER = "?com\.supernutrition\.ios"?;\s*PRODUCT_NAME = "?([^";\n]+)"?',
    ios_project,
))
native_target_ids = {
    name: identifier
    for identifier, name in re.findall(
        r'(?ms)^\s*([A-Za-z0-9]+) /\* ([^*]+) \*/ = \{\s*isa = PBXNativeTarget;',
        ios_project,
    )
}
acceptance_target_id = native_target_ids.get("SuperNutritionUIAcceptance")
production_target_id = native_target_ids.get("SuperNutrition")
if acceptance_target_id is None or production_target_id is None:
    fail("iOS Xcode native target names are missing")
if len(acceptance_product_names) != 1 or acceptance_product_names != {"SuperNutritionUIAcceptance"}:
    fail("iOS acceptance target product name is missing or collides with production")
if len(production_product_names) != 1 or production_product_names != {"SuperNutrition"}:
    fail("iOS production target product name does not match the identity manifest")
if len(re.findall(r'PRODUCT_BUNDLE_IDENTIFIER = "?com\.supernutrition\.ios\.uiacceptance"?;', ios_project)) < 2:
    fail("iOS acceptance bundle identifier is missing from all host configurations")
if len(re.findall(r'PRODUCT_BUNDLE_IDENTIFIER = "?com\.supernutrition\.ios"?;', ios_project)) < 2:
    fail("iOS production bundle identifier is missing from all host configurations")
scheme_root = repo_root / "mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/xcshareddata/xcschemes"
acceptance_scheme = (scheme_root / "SuperNutritionUIAcceptance.xcscheme").read_text(encoding="utf-8")
production_scheme_path = scheme_root / "SuperNutrition.xcscheme"
if not production_scheme_path.is_file():
    fail("iOS production shared scheme is missing")
production_scheme = production_scheme_path.read_text(encoding="utf-8")
if f'BlueprintIdentifier = "{acceptance_target_id}"' not in acceptance_scheme or \
        'BuildableName = "SuperNutritionUIAcceptance.app"' not in acceptance_scheme:
    fail("iOS acceptance scheme does not build the acceptance product")
if f'BlueprintIdentifier = "{production_target_id}"' not in production_scheme or \
        'BuildableName = "SuperNutrition.app"' not in production_scheme:
    fail("iOS production scheme does not build the production product")

# AD-37: web and backend begin in `preselection` and change atomically to
# `active` in their own reviewed toolchain stories. Freezing them at
# `preselection` would make that transition impossible, so this asserts the
# record is INTERNALLY CONSISTENT for whichever state it declares, and that an
# active product actually carries its activation evidence.
for relative in ("web/landing/product-lifecycle.toml", "backend/mobile-api/product-lifecycle.toml"):
    lifecycle = tomllib.loads((repo_root / relative).read_text(encoding="utf-8"))
    state = lifecycle.get("lifecycle")

    if state == "preselection":
        if lifecycle.get("quality_claim") is not False:
            fail(f"{relative} preselection must not claim quality")
        if lifecycle.get("production_source_count") != 0:
            fail(f"{relative} preselection must have no production source")
        if lifecycle.get("runtime") != "unselected":
            fail(f"{relative} preselection must not select a runtime")
    elif state == "active":
        if lifecycle.get("quality_claim") is not True:
            fail(f"{relative} active must claim quality")
        for required in ("dependency_manifest", "lock_state", "runtime",
                         "deployable_artifact", "quality_entrypoint", "product_inventory"):
            value = lifecycle.get(required)
            if not value or value == "absent" or value == "unselected":
                fail(f"{relative} active is missing activation evidence: {required}")
        if lifecycle.get("production_source_count", 0) <= 0:
            fail(f"{relative} active must declare production source")
        inventory_path = repo_root / relative.replace("product-lifecycle.toml", "product-inventory.toml")
        if not inventory_path.is_file():
            fail(f"{relative} active requires a product inventory")
        inventory = tomllib.loads(inventory_path.read_text(encoding="utf-8"))
        if inventory.get("lifecycle") != "active":
            fail(f"{relative} inventory disagrees with the lifecycle record")
        if not inventory.get("unit"):
            fail(f"{relative} active inventory must be nonempty")
    else:
        fail(f"{relative} declares an unknown lifecycle state: {state!r}")

print("product-identity: OK (mobile, web landing, store, and technical identifiers agree)")
PY
