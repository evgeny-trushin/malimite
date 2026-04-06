![Malimite logo](https://github.com/LaurieWired/Malimite/blob/main/media/malimite_logo.png)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/LaurieWired/Malimite)](https://github.com/LaurieWired/Malimite/releases)
[![GitHub stars](https://img.shields.io/github/stars/LaurieWired/Malimite)](https://github.com/LaurieWired/Malimite/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/LaurieWired/Malimite)](https://github.com/LaurieWired/Malimite/network/members)
[![GitHub contributors](https://img.shields.io/github/contributors/LaurieWired/Malimite)](https://github.com/LaurieWired/Malimite/graphs/contributors)
[![Follow @lauriewired](https://img.shields.io/twitter/follow/lauriewired?style=social)](https://twitter.com/lauriewired)

# Description

Malimite is an iOS and macOS decompiler designed to help researchers analyze and decode IPA files and Application Bundles.

Built on top of Ghidra decompilation to offer direct support for Swift, Objective-C, and Apple resources.

# Quick Start: Ghidra Setup Scripts

Three helper scripts are included to manage a local Ghidra installation. No manual download or Java configuration is needed.

### `install_ghidra.sh`

Downloads and installs Ghidra 10.4 and Amazon Corretto JDK 17. Handles everything automatically:
- Downloads Ghidra and JDK if not already present
- Extracts both archives
- Sets `JAVA_HOME` and `PATH` for the session
- Starts the Ghidra shared project server
- Launches the Ghidra GUI

```bash
./install_ghidra.sh
```

### `run_ghidra.sh`

Downloads the **latest** Ghidra release from GitHub (if not cached), extracts it, installs any bundled plugins, and launches Ghidra. Supports resuming from a failed step:

```bash
# Normal run (downloads if needed, extracts, launches)
./run_ghidra.sh

# Resume from a specific step
./run_ghidra.sh --step extract    # skip download, re-extract
./run_ghidra.sh --step install    # skip download+extract, re-install plugin
./run_ghidra.sh --step launch     # just launch
```

Downloaded archives are cached in `ghidra_downloads/` and extracted to `ghidra_dist/`. Both directories are git-ignored.

### `stop_ghidra.sh`

Stops the Ghidra shared project server:

```bash
./stop_ghidra.sh
```


![Malimite Features](https://github.com/LaurieWired/Malimite/blob/main/media/malimite_features_github.png)


# Features
- Multi-Platform
  - Mac, Windows, Linux
- Direct support for IPA and bundle files
- Auto decodes iOS resources
- Avoids lib code decompilation
- Reconstructs Swift classes
- **Built-in LLM method translation**
- Standalone Mach-O binary analysis (no .app bundle required)
- CLI mode with architecture selection and auto-export
- Batch export of decompiled source, strings, and class metadata


# Installation

A precompiled JAR file is provided in the [Releases Page](https://github.com/LaurieWired/Malimite/releases/)

For full Installation steps consult the Wiki.

### Build from Source

```bash
mvn clean package
```

This produces `target/malimite-1.0-SNAPSHOT.jar`. Copy the Ghidra bridge script alongside:

```bash
mkdir -p target/DecompilerBridge/ghidra
cp DecompilerBridge/ghidra/DumpClassData.java target/DecompilerBridge/ghidra/
```

# Usage

### Check out the **[Wiki](https://github.com/LaurieWired/Malimite/wiki)** for more details.

### GUI Mode

```bash
# Launch the GUI
./run.sh

# Open a file directly
./run.sh ./test.app
```

### CLI Mode

The `run.sh` wrapper handles building, path resolution, and cleanup automatically.

```bash
# Analyze an ARM64 binary
./run.sh --arch arm64 ./test.app

# Analyze and export decompiled source to a directory
./run.sh --arch arm64 --export ./output ./test.app

# Clean previous analysis before re-running
./run.sh --clean --arch arm64 ./test.app

# Custom JVM heap size (default: 256m)
./run.sh --heap 512m --arch arm64 ./test.app
```

### CLI Options

| Flag | Description |
|------|-------------|
| `--arch <arm64\|x86_64>` | Select architecture for universal (fat) binaries |
| `--export <dir>` | Export all decompiled code, strings, and metadata to directory |
| `--clean` | Remove previous analysis data before starting |
| `--heap <size>` | Set JVM heap size (default: `256m`) |
| `-h, --help` | Show help |

### Export Format

When using `--export`, the output directory contains:

```
output/
  README.txt       # Summary with class/function/string counts
  strings.txt      # Extracted string constants with addresses
  src/
    Global.c       # Decompiled global functions
    ClassName.c    # One file per class with all decompiled methods
    _Libraries.txt # External library function references
```

### Standalone Mach-O Support

Malimite can analyze bare Mach-O executables directly, without requiring a `.app` bundle or `Info.plist`:

```bash
./run.sh --arch arm64 ./test.app
```

For universal (fat) binaries, use `--arch` to skip the architecture selection dialog. Without it, a GUI picker will appear.

### Ghidra Configuration

Malimite requires Ghidra for decompilation. Set the Ghidra path in Preferences or ensure it is available at the default location. The headless analyzer memory can be configured in `<ghidra>/support/analyzeHeadless` via the `MAXMEM` setting.


# Contribute
- Make a pull request
- Add an Example to our Wiki
- Report an error/issue
- Suggest an improvement
- Share with others or give a star!

Your contributions are greatly appreciated and will help make Malimite an even more powerful and versatile tool for the iOS and macOS Reverse Engineering community.

# License

Malimite is licensed under the Apache 2.0 License. See the [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) file for more information.
