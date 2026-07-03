#!/bin/bash

set -e

usage() {
  echo "Usage: $0 <version> [--no-increment-code] [--no-commit]"
}

VERSION=""
INCREMENT_CODE=true
CREATE_COMMIT=true

while [ $# -gt 0 ]; do
  case "$1" in
    --no-increment-code)
      INCREMENT_CODE=false
      ;;
    --no-commit)
      CREATE_COMMIT=false
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
    *)
      if [ -z "$VERSION" ]; then
        VERSION="$1"
      else
        echo "Unexpected argument: $1"
        usage
        exit 1
      fi
      ;;
  esac
  shift
done

if [ -z "$VERSION" ]; then
  usage
  exit 1
fi

GRADLE_FILE="app/build.gradle"

# Update versionName
sed -i '' "s/versionName \".*\"/versionName \"$VERSION\"/" "$GRADLE_FILE"

# Determine versionCode
CURRENT_CODE=$(grep versionCode "$GRADLE_FILE" | head -1 | grep -o '[0-9]\+')
NEW_CODE="$CURRENT_CODE"

if [ "$INCREMENT_CODE" = true ]; then
  NEW_CODE=$((CURRENT_CODE + 1))
  sed -i '' "s/versionCode $CURRENT_CODE/versionCode $NEW_CODE/" "$GRADLE_FILE"
fi

# Commit changes if requested
if [ "$CREATE_COMMIT" = true ]; then
  git add "$GRADLE_FILE"
  git commit -m "Create new release: $VERSION ($NEW_CODE)"
  echo "Created commit for release $VERSION."
else
  echo "Skipped commit (--no-commit); tag will be created on the latest existing commit."
fi

# Always create a tag for the release
git tag "v$VERSION"
echo "Created tag v$VERSION."

if [ "$INCREMENT_CODE" = true ]; then
  echo "Updated to versionName $VERSION and incremented versionCode to $NEW_CODE."
else
  echo "Updated to versionName $VERSION and kept versionCode at $NEW_CODE."
fi
