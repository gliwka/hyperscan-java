#!/bin/bash

# Ensure to exit on all kinds of errors
set -xeu
set -o pipefail

echo Building on OS X
cd /tmp/hyperscan

echo Installing dependencies
brew install ragel

echo Kicking off cmake build
mkdir build && cd build && cmake -DBUILD_SHARED_LIBS=YES ..
make

echo Removing debug symbols
strip -x lib/libhs.dylib

echo Running part of the hyperscan unit tests to verify the build is working
bin/unit-hyperscan --gtest_filter=HyperscanTest\*

echo Replacing library in git repository
mkdir -p $TRAVIS_BUILD_DIR/src/main/resources/darwin
cp lib/libhs.dylib $TRAVIS_BUILD_DIR/src/main/resources/darwin