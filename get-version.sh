#!/bin/bash
# Retrieves the versionName from app/build.gradle

gradle_file="app/build.gradle"

version=$(grep -m 1 'versionName' "$gradle_file" | sed -E 's/.*versionName[[:space:]]+"([^"]+)".*/\1/')
echo "$version"
