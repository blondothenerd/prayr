#!/usr/bin/env bash
set -euo pipefail

# Lightweight Android build that does not require a Gradle installation.
#
# Default behaviour:
#   - builds an installable APK;
#   - creates a LOCAL development signing key under .local-signing/;
#   - never writes signing credentials into tracked source files.
#
# For a stable release key, set all four variables before running:
#   PRAYR_KEYSTORE=/secure/path/prayr-release.keystore
#   PRAYR_KEY_ALIAS=prayr
#   PRAYR_KEYSTORE_PASSWORD='...'
#   PRAYR_KEY_PASSWORD='...'
#
# Keep the release keystore outside the repository and back it up securely.

project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_dir" ]]; then
  echo "Set ANDROID_SDK_ROOT or ANDROID_HOME before building." >&2
  exit 1
fi

build_tools="${BUILD_TOOLS_VERSION:-35.0.0}"
platform="${ANDROID_PLATFORM:-android-35}"
tools="$sdk_dir/build-tools/$build_tools"
android_jar="$sdk_dir/platforms/$platform/android.jar"
work="$project_dir/build/manual"
dist="$project_dir/dist"
version_name="1.2.0"
version_code="5"

for required in aapt2 d8 zipalign apksigner; do
  if [[ ! -x "$tools/$required" ]]; then
    echo "Missing Android build tool: $tools/$required" >&2
    exit 1
  fi
done
if [[ ! -f "$android_jar" ]]; then
  echo "Missing Android platform: $android_jar" >&2
  exit 1
fi

mkdir -p "$work/compiled" "$work/generated" "$work/classes" "$work/dex" "$dist"
find "$work/compiled" "$work/generated" "$work/classes" "$work/dex" -mindepth 1 -delete

"$tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$work/compiled"
"$tools/aapt2" link \
  -o "$work/prayr-unsigned.apk" \
  -I "$android_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --java "$work/generated" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code "$version_code" \
  --version-name "$version_name" \
  -R "$work"/compiled/*.flat

javac -source 8 -target 8 -classpath "$android_jar" -d "$work/classes" \
  $(find "$project_dir/app/src/main/java" "$work/generated" -name '*.java' | sort)
"$tools/d8" --lib "$android_jar" --min-api 26 --output "$work/dex" \
  $(find "$work/classes" -name '*.class' | sort)
zip -q -j "$work/prayr-unsigned.apk" "$work/dex/classes.dex"
"$tools/zipalign" -f 4 "$work/prayr-unsigned.apk" "$work/prayr-aligned.apk"

if [[ -n "${PRAYR_KEYSTORE:-}" || -n "${PRAYR_KEY_ALIAS:-}" || -n "${PRAYR_KEYSTORE_PASSWORD:-}" || -n "${PRAYR_KEY_PASSWORD:-}" ]]; then
  : "${PRAYR_KEYSTORE:?Set PRAYR_KEYSTORE}"
  : "${PRAYR_KEY_ALIAS:?Set PRAYR_KEY_ALIAS}"
  : "${PRAYR_KEYSTORE_PASSWORD:?Set PRAYR_KEYSTORE_PASSWORD}"
  : "${PRAYR_KEY_PASSWORD:?Set PRAYR_KEY_PASSWORD}"
  keystore="$PRAYR_KEYSTORE"
  key_alias="$PRAYR_KEY_ALIAS"
  store_password="$PRAYR_KEYSTORE_PASSWORD"
  key_password="$PRAYR_KEY_PASSWORD"
else
  local_signing="$project_dir/.local-signing"
  keystore="$local_signing/prayr-local.keystore"
  key_alias="prayr-local"
  store_password="prayr-local-only"
  key_password="prayr-local-only"
  mkdir -p "$local_signing"
  if [[ ! -f "$keystore" ]]; then
    echo "Creating local development signing key (ignored by Git)."
    keytool -genkeypair \
      -keystore "$keystore" \
      -storepass "$store_password" \
      -alias "$key_alias" \
      -keypass "$key_password" \
      -dname "CN=blondothenerd,OU=Development,O=blondothenerd" \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 >/dev/null 2>&1
  fi
fi

output="$dist/prayr-v${version_name}.apk"
"$tools/apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$key_alias" \
  --ks-pass "pass:$store_password" \
  --key-pass "pass:$key_password" \
  --out "$output" \
  "$work/prayr-aligned.apk"
"$tools/apksigner" verify --verbose "$output"
echo "Built $output"
