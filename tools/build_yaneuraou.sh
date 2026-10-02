#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
NDK="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
APP_PLATFORM="${APP_PLATFORM:-android-29}"

YANEURAOU_DIR="$ROOT_DIR/third_party/YaneuraOu"
PATCH="$ROOT_DIR/patches/yaneuraou/0001-fix-android-neon.patch"

NNUE_TYPES=(
    "HALFKP_256X2_32_32"
    "HALFKP_512X2_8_64"
    "HALFKP_768X2_16_64"
)

ARCHS=(
    "x86_64"
    "arm64-v8a"
)

JOBS="${JOBS:-4}"

PATCH_APPLIED=false
GENERATED_HEADERS=()

cleanup() {
    if $PATCH_APPLIED; then
        git -C "$YANEURAOU_DIR" apply -R "$PATCH" || true
    fi

    for header in "${GENERATED_HEADERS[@]}"; do
        rm -f "$header"
    done
}

trap cleanup EXIT INT TERM

if [[ ! -x "$NDK/ndk-build" ]]; then
    echo "ndk-build not found: $NDK/ndk-build" >&2
    exit 1
fi

# Ensure the patch can be applied cleanly.
git -C "$YANEURAOU_DIR" apply --check "$PATCH"
git -C "$YANEURAOU_DIR" apply "$PATCH"
PATCH_APPLIED=true

# Generate architecture headers.
for TYPE in "${NNUE_TYPES[@]}"; do
    HEADER="$YANEURAOU_DIR/source/eval/nnue/architectures/${TYPE}.h"

    python3 \
        "$YANEURAOU_DIR/source/eval/nnue/architectures/nnue_arch_gen.py" \
        "YANEURAOU_ENGINE_NNUE_${TYPE}" \
        "$YANEURAOU_DIR/source/eval/nnue/architectures"

    GENERATED_HEADERS+=("$HEADER")
done

# Build YaneuraOu.
for ARCH in "${ARCHS[@]}"; do
    for TYPE in "${NNUE_TYPES[@]}"; do
        TYPE_LOWER="$(echo "$TYPE" | tr '[:upper:]' '[:lower:]')"
        ENGINE_NAME="YaneuraOu_NNUE_${TYPE_LOWER}"
        BUILD_DIR="$ROOT_DIR/build/yaneuraou/$TYPE"

        echo "Building ${ENGINE_NAME} for ${ARCH}"

        COMMON_ARGS=(
            "NDK_PROJECT_PATH=$YANEURAOU_DIR"
            "APP_BUILD_SCRIPT=$YANEURAOU_DIR/script/jni/Android.mk"
            "NDK_APPLICATION_MK=$YANEURAOU_DIR/script/jni/Application.mk"
            "APP_ABI=$ARCH"
            "APP_PLATFORM=$APP_PLATFORM"
            "YANEURAOU_EDITION=YANEURAOU_ENGINE_NNUE"
            "ENGINE_NAME=$ENGINE_NAME"
            "EXTRA_CPPFLAGS=-DNNUE_ARCHITECTURE_HEADER=\\\"architectures/${TYPE}.h\\\""
            "NDK_OUT=$BUILD_DIR/obj"
            "NDK_LIBS_OUT=$BUILD_DIR/libs"
        )

        "$NDK/ndk-build" clean "${COMMON_ARGS[@]}"
        "$NDK/ndk-build" "-j" "${COMMON_ARGS[@]}"

        JNI_PATH="$JNI_DIR/$ARCH"
        mkdir -p "$JNI_PATH"

        LIB_SRC=YaneuraOu_NNUE_${TYPE_LOWER}_${ARCH}
        LIB_DST=libYaneuraOu_NNUE_${TYPE_LOWER}_${ARCH}.so
        cp "$BUILD_DIR/libs/$ARCH/$LIB_SRC" \
            "$JNI_PATH/$LIB_DST"
    done
done
