#!/bin/bash
# Script to install Ghidra 10 and configure Java

GHIDRA_VERSION="ghidra_10.4_PUBLIC"
GHIDRA_ZIP="${GHIDRA_VERSION}_20230928.zip"
GHIDRA_URL="https://github.com/NationalSecurityAgency/ghidra/releases/download/Ghidra_10.4_build/${GHIDRA_ZIP}"
JAVA_VERSION="jdk-17.0.9+9"
JAVA_TAR="${JAVA_VERSION}.tar.gz"
JAVA_URL="https://corretto.aws/downloads/latest/amazon-corretto-17-x64-macos-jdk.tar.gz"  # Replace with the actual Java tarball URL

# Download Ghidra if it's not already present
if [ ! -f "$GHIDRA_ZIP" ]; then
    curl -L "$GHIDRA_URL" -o "$GHIDRA_ZIP" || { echo "Failed to download Ghidra"; exit 1; }
fi

# Extract Ghidra if it's not already extracted
if [ ! -d "$GHIDRA_VERSION" ]; then
    unzip -q "$GHIDRA_ZIP" || { echo "Failed to extract Ghidra"; exit 1; }
fi

# Download Java if it's not already present
if [ ! -f "$JAVA_TAR" ]; then
    curl -L "$JAVA_URL" -o "$JAVA_TAR" || { echo "Failed to download Java"; exit 1; }
fi

# Extract Java if it's not already extracted
if [ ! -d "$JAVA_VERSION" ]; then
    tar -xzf "$JAVA_TAR" || { echo "Failed to extract Java"; exit 1; }
fi

# Set JAVA_HOME and add it to the PATH
export JAVA_HOME="$(pwd)/${JAVA_VERSION}/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Check if JAVA_HOME and PATH are set correctly
env | grep -E 'JAVA_HOME|PATH'

# Start Ghidra Server
"${GHIDRA_VERSION}/server/ghidraSvr" start

# Run Ghidra
"${GHIDRA_VERSION}/ghidraRun"
