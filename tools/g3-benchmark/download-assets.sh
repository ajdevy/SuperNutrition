#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
MODEL_URL='https://huggingface.co/AlexKoff88/mobilenet_v2_food101/resolve/main/mobilenet_v2_food101.onnx?download=true'
MODEL_SHA='af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890'

sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

download_verified() {
  local url="$1"
  local destination="$2"
  local expected="$3"
  mkdir -p "$(dirname "$destination")"
  if [[ ! -f "$destination" || "$(sha256 "$destination")" != "$expected" ]]; then
    curl --fail --location --retry 3 --retry-delay 1 --silent --show-error \
      --user-agent 'SuperNutrition-G3-research/1.0' "$url" --output "$destination"
  fi
  [[ "$(sha256 "$destination")" == "$expected" ]] || {
    printf 'sha256 mismatch: %s\n' "$destination" >&2
    return 1
  }
}

MODEL_PATH="$ROOT_DIR/ios-runner/Resources/mobilenet_v2_food101.onnx"
download_verified "$MODEL_URL" "$MODEL_PATH" "$MODEL_SHA"
mkdir -p "$ROOT_DIR/android-runner/app/src/main/assets"
cp "$MODEL_PATH" "$ROOT_DIR/android-runner/app/src/main/assets/mobilenet_v2_food101.onnx"

declare -a NAMES=(
  'steak-side-plate'
  'fish-side-plate'
  'chopped-salad'
  'buddha-bowl'
  'salmon-grain-plate'
)
declare -a FILES=(
  'Steak_dinner_with_vegetables.jpg'
  'Pike_and_vegetables.jpg'
  'Mixed_salad_(Kosambri_or_Kachumber).jpg'
  'BuddhaBowlLot.jpg'
  'Rice,_beans_and_Salmon_Fish.jpg'
)
declare -a SHAS=(
  '619a90aed1f8307ad39ecadefa318a663d27885403bb28d3fbe2c0a1828154b5'
  '8e7e6dbf90e33c6116346600ef48f1a02de547f455613184dc829bb81e971f1d'
  'f0c02d4deae3f4b950b4ef88b2467e23d650d6f5ea195bedef178bc28375101c'
  '503be94cd0e3345178b838c1d50e79d3f28b3cf70449e56708d84178e0fa4c67'
  'a6c0467ff4c49694f28a308a57a09029cc83be729fa76aea4300c7d8fd54c20a'
)

for index in "${!NAMES[@]}"; do
  url="https://commons.wikimedia.org/wiki/Special:FilePath/${FILES[$index]}"
  destination="$ROOT_DIR/ios-runner/Resources/fixtures/${NAMES[$index]}.jpg"
  download_verified "$url" "$destination" "${SHAS[$index]}"
done

mkdir -p "$ROOT_DIR/android-runner/app/src/main/assets/fixtures"
cp "$ROOT_DIR/ios-runner/Resources/fixtures/"*.jpg \
  "$ROOT_DIR/android-runner/app/src/main/assets/fixtures/"
printf 'Verified G-3 model and %s fixtures.\n' "${#NAMES[@]}"
