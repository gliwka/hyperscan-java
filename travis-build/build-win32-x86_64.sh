#!/bin/bash

# Ensure to exit on all kinds of errors
set -xeu
set -o pipefail

powershell -C "choco install -y cmake --installargs 'ADD_CMAKE_TO_PATH=System'"
powershell -C "choco install -y python2"
powershell -C "choco install -y cygwin"
powershell -C "choco install dos2unix"
/c/tools/cygwin/cygwinsetup.exe --root C:\\tools\\cygwin --local-package-dir C:\\tools\\cygwin\\packages --no-admin --no-desktop --no-startmenu --quiet-mode --verbose --packages bash,gcc-core,gcc-g++,make,wget
cmd.exe /C "setx PATH \"C:\tools\cygwin\bin;C:\tools\cygwin\usr\local\bin;%PATH%\""
cmd.exe /C "setx /M PATH \"C:\tools\cygwin\bin;C:\tools\cygwin\usr\local\bin;%PATH%\""

dos2unix travis-build/cygwin-script.sh
cmd.exe /C "SET PATH=C:\tools\cygwin\bin;C:\tools\cygwin\usr\local\bin;%PATH% && bash.exe travis-build/cygwin-script.sh"

#reset dos2unix changes
git reset --hard