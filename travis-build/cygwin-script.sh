#!/bin/bash

# Ensure to exit on all kinds of errors
set -xeu
set -o pipefail

wget https://dl.bintray.com/boostorg/release/1.65.1/source/boost_1_65_1.tar.gz
tar -zxf boost_1_65_1.tar.gz
mv boost_1_65_1/boost hyperscan/include/boost

wget https://www.colm.net/files/ragel/ragel-6.10.tar.gz
tar -zxf ragel-6.10.tar.gz
cd ragel-6.10
./configure
make
make install
cd ..

mkdir hyperscan/build
cd hyperscan/build
cmake -G "Visual Studio 15 2017" -DCMAKE_BUILD_TYPE=MinSizeRel -DBUILD_SHARED_LIBS=YES .. -A x64 -T host=x64
cmake --build .

mkdir -p $TRAVIS_BUILD_DIR/src/main/resources/win32-x86-64
cp bin/hs.dll $TRAVIS_BUILD_DIR/src/main/resources/win32-x86-64