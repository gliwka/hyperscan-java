#!/bin/bash

# Ensure to exit on all kinds of errors
set -eu
set -o pipefail

echo "Checking rebuild command..."
# Check if commit message contains #rebuild cimmand
if [ -n "$(grep '#rebuild' <<< "$TRAVIS_COMMIT_MESSAGE")" ]  ; then
    HYPERSCAN_VERSION=$(<.hyperscan-version)
    echo "Version changed to $HYPERSCAN_VERSION. Compiling and deploying new shared libraries"
    echo "Cloning the hyperscan code"
    git clone -b $HYPERSCAN_VERSION --depth 1 https://github.com/intel/hyperscan.git
    echo "Running build script for $TRAVIS_OS_NAME"
    bash travis-build/build-$TARGET_OS-$TARGET_ARCH.sh
    cd $TRAVIS_BUILD_DIR
    echo "Decrypt deployment key"
    openssl aes-256-cbc -K $encrypted_13a362132342_key -iv $encrypted_13a362132342_iv -in travis-build/deploy-key.enc -out /tmp/deploy-key -d
    chmod 600 /tmp/deploy-key
    eval `ssh-agent -s`
    ssh-add /tmp/deploy-key
    git add src/main/resources
    git checkout $TRAVIS_BRANCH
    git config user.name "Travis Build Script"
    git config user.email "matthias@gliwka.eu"
    git commit -m "Add shared libraries for hyperscan $HYPERSCAN_VERSION ($TARGET_OS $TARGET_ARCH) [ci skip]"
    REPO=`git config remote.origin.url`
    SSH_REPO=${REPO/https:\/\/github.com\//git@github.com:}
    mkdir -p ~/.ssh
    cat travis-build/github-hostkey >> ~/.ssh/known_hosts
    git pull --rebase $SSH_REPO $TRAVIS_BRANCH
    git push $SSH_REPO $TRAVIS_BRANCH
else
    echo "No rebuild command detected"
fi
