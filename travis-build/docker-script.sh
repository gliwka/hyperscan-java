#!/bin/bash

# Ensure to exit on all kinds of errors
set -eu
set -o pipefail

echo Installing wget, gcc, g++, tar
yum -y install wget gcc gcc-c++ tar

echo Adding repo with gcc 4.8.2 and related build tools
wget https://people.centos.org/tru/devtools-2/devtools-2.repo -O /etc/yum.repos.d/devtools-2.repo

echo Compiling and installing ragel
cd /tmp
wget https://www.colm.net/files/ragel/ragel-6.10.tar.gz
tar -zxf ragel-6.10.tar.gz
cd ragel-6.10
./configure
make
make install

echo Fetching boost headers
cd /tmp
wget https://dl.bintray.com/boostorg/release/1.65.1/source/boost_1_65_1.tar.gz
tar -zxf boost_1_65_1.tar.gz

echo Linking boost headers
ln -s /tmp/boost_1_65_1/boost /tmp/hyperscan/include/boost

echo Installing tools
yum -y install devtoolset-2-gcc-c++ cmake devtoolset-2-binutils

echo Enabling devtoolset
set +u
source /opt/rh/devtoolset-2/enable
set -u

cd /tmp/hyperscan
echo Kicking off hyperscan cmake build
mkdir build && cd build && cmake -DBUILD_SHARED_LIBS=YES ..
make

echo Removing debug symbols
strip lib/libhs.so

echo Runing part of the hyperscan unit tests to verify the build is working
bin/unit-hyperscan --gtest_filter=HyperscanTest\*