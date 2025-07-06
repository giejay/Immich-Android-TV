#!/bin/bash

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION="$1"
GRADLE_FILE="app/build.gradle"

# Update versionName
sed -i '' "s/versionName \".*\"/versionName \"$VERSION\"/" "$GRADLE_FILE"

# Increment versionCode
CURRENT_CODE=$(grep versionCode "$GRADLE_FILE" | head -1 | grep -o '[0-9]\+')
NEW_CODE=$((CURRENT_CODE + 1))
sed -i '' "s/versionCode $CURRENT_CODE/versionCode $NEW_CODE/" "$GRADLE_FILE"

# Commit changes
git add "$GRADLE_FILE"
git commit -m "Create new release: $VERSION ($NEW_CODE)"

# Create git tag
git tag "v$VERSION"

echo "Updated to versionName $VERSION, versionCode $NEW_CODE, and created tag v$VERSION."