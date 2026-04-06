#!/bin/bash
# Script to stop Ghidra server

JAVA_VERSION="jdk-17.0.9+9"

# Set JAVA_HOME and add it to the PATH
export JAVA_HOME="$(pwd)/${JAVA_VERSION}/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Check if JAVA_HOME and PATH are set correctly
env | grep -E 'JAVA_HOME|PATH'

# Stop Ghidra Server
"./ghidra/server/ghidraSvr" stop
