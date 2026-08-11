#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SOURCE_MODEL_URL='https://huggingface.co/onnx-community/swin-finetuned-food101-ONNX/resolve/main/onnx/model.onnx?download=true'
SOURCE_MODEL_SHA='f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83'
MODEL_SHA='9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df'
MODEL_NAME='swin-finetuned-food101-matmul-int8.onnx'
LABELS_SHA='eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820'
YOLOX_NANO_URL='https://github.com/Megvii-BaseDetection/YOLOX/releases/download/0.1.1rc0/yolox_nano.onnx'
YOLOX_NANO_SHA='c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d'

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

LABELS_PATH="$ROOT_DIR/food101-labels.txt"
[[ "$(sha256 "$LABELS_PATH")" == "$LABELS_SHA" ]] || {
  printf 'sha256 mismatch: %s\n' "$LABELS_PATH" >&2
  exit 1
}
MODEL_PATH="$ROOT_DIR/ios-runner/Resources/$MODEL_NAME"
SOURCE_MODEL_PATH="$ROOT_DIR/ios-runner/Resources/.swin-finetuned-food101-source.onnx"
if [[ ! -f "$MODEL_PATH" || "$(sha256 "$MODEL_PATH")" != "$MODEL_SHA" ]]; then
  download_verified "$SOURCE_MODEL_URL" "$SOURCE_MODEL_PATH" "$SOURCE_MODEL_SHA"
  command -v uv >/dev/null
  uv run --with onnxruntime==1.23.2 --with onnx==1.22.0 \
    python "$ROOT_DIR/quantize-model.py" "$SOURCE_MODEL_PATH" "$MODEL_PATH"
fi
[[ "$(sha256 "$MODEL_PATH")" == "$MODEL_SHA" ]] || {
  printf 'sha256 mismatch: %s\n' "$MODEL_PATH" >&2
  exit 1
}
mkdir -p "$ROOT_DIR/android-runner/app/src/main/assets"
cp "$MODEL_PATH" "$ROOT_DIR/android-runner/app/src/main/assets/$MODEL_NAME"
cp "$LABELS_PATH" "$ROOT_DIR/android-runner/app/src/main/assets/food101-labels.txt"
download_verified "$YOLOX_NANO_URL" \
  "$ROOT_DIR/android-runner/app/src/main/assets/yolox_nano.onnx" \
  "$YOLOX_NANO_SHA"

declare -a NAMES=(
  'steak-side-plate'
  'fish-side-plate'
  'chopped-salad'
  'buddha-bowl'
  'salmon-grain-plate'
  'steak'
  'greek-salad'
  'salmon-on-grill'
  'sushi'
  'waffles'
)
declare -a FILES=(
  'Steak_dinner_with_vegetables.jpg'
  'Pike_and_vegetables.jpg'
  'Mixed_salad_(Kosambri_or_Kachumber).jpg'
  'BuddhaBowlLot.jpg'
  'Rice,_beans_and_Salmon_Fish.jpg'
  'Steak_(1).jpg'
  'Greek_salad.jpg'
  'Salmon_on_grill.jpg'
  'Sushi_(29678778543).jpg'
  'Waffles_(1).jpg'
)
declare -a SHAS=(
  '619a90aed1f8307ad39ecadefa318a663d27885403bb28d3fbe2c0a1828154b5'
  '8e7e6dbf90e33c6116346600ef48f1a02de547f455613184dc829bb81e971f1d'
  'f0c02d4deae3f4b950b4ef88b2467e23d650d6f5ea195bedef178bc28375101c'
  '503be94cd0e3345178b838c1d50e79d3f28b3cf70449e56708d84178e0fa4c67'
  'a6c0467ff4c49694f28a308a57a09029cc83be729fa76aea4300c7d8fd54c20a'
  'daeadedd73613ee6bbbfd2f123a7288dfa062972f6f2896cadf9f53c15c67b38'
  'c122bec04ba7a2d81de364147be154bdadee9d6d742551e67f54260bbb09b8a6'
  '7ddb6292f622629904c63104689beb48a02061ce99048bd6fb069e68e0d10c07'
  '37e0061446ae69211f6a730f0d7004b67187a277fd2d80876d4093e862c0b57f'
  '9c14f3de3376a854537537a625f153ca8ec7a1048e733ca5738353416ff2a39e'
)

for index in "${!NAMES[@]}"; do
  url="https://commons.wikimedia.org/wiki/Special:FilePath/${FILES[$index]}"
  destination="$ROOT_DIR/ios-runner/Resources/fixtures/${NAMES[$index]}.jpg"
  download_verified "$url" "$destination" "${SHAS[$index]}"
done

mkdir -p "$ROOT_DIR/android-runner/app/src/main/assets/fixtures"
cp "$ROOT_DIR/ios-runner/Resources/fixtures/"*.jpg \
  "$ROOT_DIR/android-runner/app/src/main/assets/fixtures/"
cp "$LABELS_PATH" "$ROOT_DIR/ios-runner/Resources/food101-labels.txt"
printf 'Verified G-3 model and %s fixtures.\n' "${#NAMES[@]}"
