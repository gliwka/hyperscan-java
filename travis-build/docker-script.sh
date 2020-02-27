#!/bin/bash

# Ensure to exit on all kinds of errors
set -eu
set -o pipefail

THREADS=$(nproc --all)

echo Installing wget, gcc, g++, tar, make, yum-utils
yum -y install wget gcc gcc-c++ tar make yum-utils

echo Compiling and installing ragel
cd /tmp
wget https://www.colm.net/files/ragel/ragel-6.10.tar.gz
tar -zxf ragel-6.10.tar.gz
cd ragel-6.10
./configure
make -j $THREADS
make install

echo Fetching boost headers
cd /tmp
wget https://dl.bintray.com/boostorg/release/1.65.1/source/boost_1_65_1.tar.gz
tar -zxf boost_1_65_1.tar.gz

echo Linking boost headers
ln -s /tmp/boost_1_65_1/boost /tmp/hyperscan/include/boost

echo Installing tools
yum -y install centos-release-scl
yum-config-manager --enable rhel-server-rhscl-7-rpms
yum -y install devtoolset-7-gcc-c++ devtoolset-7-gcc cmake

echo Enabling devtoolset
set +u
source /opt/rh/devtoolset-7/enable
set -u

cd /tmp/hyperscan
echo Kicking off hyperscan cmake build
mkdir build && cd build && cmake -DBUILD_AVX512=on -DCMAKE_BUILD_TYPE=MinSizeRel -DBUILD_SHARED_LIBS=YES ..
make -j $THREADS

echo Removing debug symbols
strip lib/libhs.so

echo Runing part of the hyperscan unit tests to verify the build is working
bin/unit-hyperscan --gtest_filter=HyperscanTest\*