#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_SOURCE="${ROOT_DIR}/TestPlugin.java"
API_URL="https://api.github.com/repos/NationalSecurityAgency/ghidra/releases/latest"
DOWNLOAD_DIR="${ROOT_DIR}/ghidra_downloads"
EXTRACT_DIR="${ROOT_DIR}/ghidra_dist"
CURRENT_ARCHIVE_FILE="${DOWNLOAD_DIR}/.test-current-archive"
CURRENT_INSTALL_FILE="${EXTRACT_DIR}/.test-current-ghidra-home"
START_STEP="auto"

log() {
  printf '[test-ghidra] %s\n' "$*"
}

die() {
  printf '[test-ghidra] error: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./run_ghidra.sh [--step download|extract|install|launch]

Default behavior resumes automatically:
- existing extracted install -> install plugin -> launch
- existing downloaded archive -> extract -> install plugin -> launch
- otherwise -> download -> extract -> install plugin -> launch
EOF
}

command_exists() {
  local command_name="$1"

  command -v "${command_name}" >/dev/null 2>&1
}

require_commands() {
  local command_name

  for command_name in "$@"; do
    if ! command_exists "${command_name}"; then
      die "required command not found: ${command_name}"
    fi
  done
}

parse_args() {
  while (($#)); do
    case "$1" in
      --step)
        [[ $# -ge 2 ]] || die "missing value for --step"
        START_STEP="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        die "unknown argument: $1"
        ;;
    esac
  done

  case "${START_STEP}" in
    auto|download|extract|install|launch)
      ;;
    *)
      die "invalid step '${START_STEP}'. Expected one of: download, extract, install, launch"
      ;;
  esac
}

detect_platform() {
  local uname_value

  uname_value="$(uname -s)"
  case "${uname_value}" in
    Darwin)
      platform="macos"
      ;;
    Linux)
      platform="linux"
      ;;
    *)
      die "unsupported platform: ${uname_value}"
      ;;
  esac
}

find_existing_install() {
  local candidate_dir
  local recorded_home
  local selected_home=""

  if [[ ! -d "${EXTRACT_DIR}" ]]; then
    return 1
  fi

  if [[ -f "${CURRENT_INSTALL_FILE}" ]]; then
    recorded_home="$(<"${CURRENT_INSTALL_FILE}")"
    if [[ -x "${recorded_home}/ghidraRun" ]]; then
      ghidra_home="${recorded_home}"
      return 0
    fi
  fi

  for candidate_dir in "${EXTRACT_DIR}"/*; do
    if [[ ! -x "${candidate_dir}/ghidraRun" ]]; then
      continue
    fi

    if [[ -z "${selected_home}" || "${candidate_dir}/ghidraRun" -nt "${selected_home}/ghidraRun" ]]; then
      selected_home="${candidate_dir}"
    fi
  done

  if [[ -z "${selected_home}" ]]; then
    return 1
  fi

  ghidra_home="${selected_home}"
  return 0
}

find_existing_archive() {
  local candidate_path
  local recorded_archive
  local selected_archive=""

  if [[ ! -d "${DOWNLOAD_DIR}" ]]; then
    return 1
  fi

  if [[ -f "${CURRENT_ARCHIVE_FILE}" ]]; then
    recorded_archive="$(<"${CURRENT_ARCHIVE_FILE}")"
    if [[ -f "${recorded_archive}" ]]; then
      archive_path="${recorded_archive}"
      asset_name="$(basename "${archive_path}")"
      return 0
    fi
  fi

  for candidate_path in "${DOWNLOAD_DIR}"/*.zip; do
    if [[ ! -f "${candidate_path}" ]]; then
      continue
    fi

    if [[ -z "${selected_archive}" || "${candidate_path}" -nt "${selected_archive}" ]]; then
      selected_archive="${candidate_path}"
    fi
  done

  if [[ -z "${selected_archive}" ]]; then
    return 1
  fi

  archive_path="${selected_archive}"
  asset_name="$(basename "${archive_path}")"
  return 0
}

record_current_archive() {
  mkdir -p "${DOWNLOAD_DIR}"
  printf '%s\n' "${archive_path}" > "${CURRENT_ARCHIVE_FILE}"
}

record_current_install() {
  mkdir -p "${EXTRACT_DIR}"
  printf '%s\n' "${ghidra_home}" > "${CURRENT_INSTALL_FILE}"
}

fetch_latest_release_asset() {
  local release_json

  require_commands curl sed head

  log "Resolving latest Ghidra release metadata"
  if ! release_json="$(curl -fsSL "${API_URL}" 2>&1)"; then
    printf '%s\n' "${release_json}" >&2
    return 1
  fi

  asset_url="$(printf '%s\n' "${release_json}" | sed -n 's/.*"browser_download_url":[[:space:]]*"\(https:[^"]*\/ghidra_[^"]*\.zip\)".*/\1/p' | head -n 1)"

  if [[ -z "${asset_url}" ]]; then
    log "Failed to parse a Ghidra zip asset from the release metadata"
    printf '%s\n' "${release_json}" >&2
    return 1
  fi

  asset_name="$(basename "${asset_url}")"
  archive_path="${DOWNLOAD_DIR}/${asset_name}"
  return 0
}

download_release_if_needed() {
  require_commands curl
  mkdir -p "${DOWNLOAD_DIR}"

  if [[ -f "${archive_path}" ]]; then
    log "Using existing archive ${archive_path}"
    record_current_archive
    return 0
  fi

  log "Downloading ${asset_name} to ${archive_path}"
  if ! curl -fL "${asset_url}" -o "${archive_path}"; then
    return 1
  fi

  record_current_archive
  return 0
}

validate_archive() {
  local validation_output

  require_commands unzip

  [[ -f "${archive_path}" ]] || return 1

  log "Validating archive ${archive_path}"
  if ! validation_output="$(unzip -tq "${archive_path}" 2>&1)"; then
    log "Archive validation failed for ${archive_path}"
    printf '%s\n' "${validation_output}" >&2
    return 1
  fi

  log "Archive validation passed"
  return 0
}

resolve_archive_root() {
  local listing_output

  require_commands unzip awk

  log "Inspecting archive layout for ${archive_path}"
  if ! listing_output="$(unzip -Z -1 "${archive_path}" 2>&1)"; then
    log "Failed to list archive contents for ${archive_path}"
    printf '%s\n' "${listing_output}" >&2
    return 1
  fi

  archive_root="$(printf '%s\n' "${listing_output}" | awk -F/ 'NF && $1 != "" { print $1; exit }')"

  if [[ -z "${archive_root}" ]]; then
    log "Could not determine the archive root directory for ${archive_path}"
    printf '%s\n' "${listing_output}" >&2
    return 1
  fi

  ghidra_home="${EXTRACT_DIR}/${archive_root}"
  return 0
}

extract_release_if_needed() {
  local extract_output

  [[ -n "${archive_path:-}" ]] || die "no archive is selected for extraction"
  [[ -f "${archive_path}" ]] || die "archive not found: ${archive_path}"

  validate_archive || return 1
  resolve_archive_root || return 1

  mkdir -p "${EXTRACT_DIR}"

  if [[ -x "${ghidra_home}/ghidraRun" ]]; then
    log "Using existing extraction ${ghidra_home}"
    record_current_archive
    record_current_install
    return 0
  fi

  log "Extracting ${asset_name} into ${EXTRACT_DIR}"
  if ! extract_output="$(unzip -q "${archive_path}" -d "${EXTRACT_DIR}" 2>&1)"; then
    log "Extraction failed for ${archive_path}"
    if [[ -n "${extract_output}" ]]; then
      printf '%s\n' "${extract_output}" >&2
    fi
    return 1
  fi

  if [[ ! -x "${ghidra_home}/ghidraRun" ]]; then
    log "Extraction completed but ghidraRun was not found at ${ghidra_home}/ghidraRun"
    return 1
  fi

  log "Extraction complete: ${ghidra_home}"
  record_current_archive
  record_current_install
  return 0
}

ensure_existing_install() {
  if ! find_existing_install; then
    die "no extracted Ghidra install was found in ${EXTRACT_DIR}. Start from download or extract first."
  fi

  log "Using existing local install ${ghidra_home}"
}

ensure_existing_archive() {
  if ! find_existing_archive; then
    die "no downloaded Ghidra archive was found in ${DOWNLOAD_DIR}. Start from download first."
  fi

  log "Using existing archive ${archive_path}"
}

run_auto_flow() {
  if find_existing_install; then
    log "Using existing local install ${ghidra_home}"
    return 0
  fi

  if find_existing_archive; then
    log "Using existing archive ${archive_path}"
    extract_release_if_needed || return 1
    return 0
  fi

  fetch_latest_release_asset || return 1
  download_release_if_needed || return 1
  extract_release_if_needed || return 1
}

run_download_flow() {
  if find_existing_archive; then
    log "Using existing archive ${archive_path}"
  else
    fetch_latest_release_asset || return 1
    download_release_if_needed || return 1
  fi

  extract_release_if_needed || return 1
}

run_extract_flow() {
  ensure_existing_archive
  extract_release_if_needed || return 1
}

install_plugin() {
  local script_dir

  [[ -f "${PLUGIN_SOURCE}" ]] || die "missing plugin source: ${PLUGIN_SOURCE}"

  script_dir="${ghidra_home}/Ghidra/Features/Base/ghidra_scripts"
  mkdir -p "${script_dir}"
  cp "${PLUGIN_SOURCE}" "${script_dir}/TestPlugin.java"
  log "Installed TestPlugin.java into ${script_dir}"
}

launch_ghidra() {
  local launcher_path

  launcher_path="${ghidra_home}/ghidraRun"
  [[ -x "${launcher_path}" ]] || die "missing Ghidra launcher: ${launcher_path}"

  case "${platform}" in
    macos)
      log "Launching Ghidra on macOS"
      exec "${launcher_path}"
      ;;
    linux)
      log "Launching Ghidra on Linux"
      exec "${launcher_path}"
      ;;
  esac
}

main() {
  parse_args "$@"
  detect_platform
  log "Starting from step: ${START_STEP}"

  case "${START_STEP}" in
    auto)
      run_auto_flow || die "could not prepare the Ghidra environment"
      record_current_install
      install_plugin
      launch_ghidra
      ;;
    download)
      run_download_flow || die "could not download and extract Ghidra"
      record_current_install
      install_plugin
      launch_ghidra
      ;;
    extract)
      run_extract_flow || die "could not extract Ghidra from the existing archive"
      record_current_install
      install_plugin
      launch_ghidra
      ;;
    install)
      ensure_existing_install
      record_current_install
      install_plugin
      launch_ghidra
      ;;
    launch)
      ensure_existing_install
      record_current_install
      launch_ghidra
      ;;
  esac
}

main "$@"
