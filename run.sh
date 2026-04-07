#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="${SCRIPT_DIR}/target"
JAR="${TARGET_DIR}/malimite-1.0-SNAPSHOT.jar"
BRIDGE_SRC="${SCRIPT_DIR}/DecompilerBridge/ghidra/DumpClassData.java"
BRIDGE_DST="${TARGET_DIR}/DecompilerBridge/ghidra"

# Build if JAR doesn't exist
if [[ ! -f "${JAR}" ]]; then
    echo "[malimite] Building..."
    /tmp/apache-maven-3.9.6/bin/mvn -f "${SCRIPT_DIR}/pom.xml" package -q
    mkdir -p "${BRIDGE_DST}"
    cp "${BRIDGE_SRC}" "${BRIDGE_DST}/"
    echo "[malimite] Build complete"
fi

# Ensure DecompilerBridge is in target
if [[ ! -f "${BRIDGE_DST}/DumpClassData.java" ]]; then
    mkdir -p "${BRIDGE_DST}"
    cp "${BRIDGE_SRC}" "${BRIDGE_DST}/"
fi

usage() {
    echo "Usage: $(basename "$0") [options] <binary>"
    echo ""
    echo "Options:"
    echo "  --arch <arm64|x86_64>   Select architecture for universal binaries"
    echo "  --export <dir>          Export decompilation to directory"
    echo "  --clean                 Remove previous analysis before starting"
    echo "  --heap <size>           JVM heap size (default: 256m)"
    echo "  -h, --help              Show this help"
    echo ""
    echo "Examples:"
    echo "  $(basename "$0") ./myapp.app"
    echo "  $(basename "$0") --arch arm64 --export ./output ./myapp.app"
    echo "  $(basename "$0") --clean --arch arm64 ~/path/to/binary"
    exit 0
}

ARCH=""
EXPORT=""
CLEAN=false
HEAP="256m"
BINARY=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch)    ARCH="$2"; shift 2 ;;
        --export)  EXPORT="$2"; shift 2 ;;
        --clean)   CLEAN=true; shift ;;
        --heap)    HEAP="$2"; shift 2 ;;
        -h|--help) usage ;;
        -*)        echo "Unknown option: $1"; usage ;;
        *)         BINARY="$1"; shift ;;
    esac
done

if [[ -z "${BINARY}" ]]; then
    # No binary specified — just launch GUI
    cd "${TARGET_DIR}" && exec java "-Xmx${HEAP}" -jar "${JAR}"
fi

# Resolve to absolute path
BINARY="$(cd "$(dirname "${BINARY}")" && pwd)/$(basename "${BINARY}")"

if [[ ! -e "${BINARY}" ]]; then
    echo "[malimite] Error: file not found: ${BINARY}"
    exit 1
fi

# Clean previous analysis if requested
if ${CLEAN}; then
    BINARY_NAME="$(basename "${BINARY}")"
    BASE_NAME="${BINARY_NAME%.*}"
    PARENT_DIR="$(dirname "${BINARY}")"
    MALIMITE_DIR="${PARENT_DIR}/${BASE_NAME}_malimite"
    if [[ -d "${MALIMITE_DIR}" ]]; then
        echo "[malimite] Removing previous analysis: ${MALIMITE_DIR}"
        rm -rf "${MALIMITE_DIR}"
    fi
fi

# Build java arguments
JAVA_ARGS=()
[[ -n "${ARCH}" ]] && JAVA_ARGS+=(--arch "${ARCH}")
if [[ -n "${EXPORT}" ]]; then
    EXPORT_ABS="$(cd "$(dirname "${EXPORT}")" 2>/dev/null && pwd)/$(basename "${EXPORT}")" 2>/dev/null || EXPORT_ABS="${EXPORT}"
    JAVA_ARGS+=(--export "${EXPORT_ABS}")
fi
JAVA_ARGS+=("${BINARY}")

cd "${TARGET_DIR}" && exec java "-Xmx${HEAP}" -jar "${JAR}" "${JAVA_ARGS[@]}"
