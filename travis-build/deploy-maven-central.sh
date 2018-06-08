#!/bin/bash
echo Decrypting pgp key for release signing
openssl aes-256-cbc -K $encrypted_1acfec761134_key -iv $encrypted_1acfec761134_iv -in travis-build/codesigning.enc -out /tmp/codesigning.asc

echo Importing pgp key into gpg
gpg --fast-import /tmp/codesigning.asc

echo Deploy to maven central
mvn -P sign --settings travis-build/mvnsettings.xml deploy