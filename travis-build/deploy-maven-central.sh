#!/bin/bash
echo Decrypting pgp key for release signing
openssl aes-256-cbc -K $encrypted_b3fbb85b63b3_key -iv $encrypted_b3fbb85b63b3_iv -in travis-build/codesigning.enc -out /tmp/codesigning.asc -d
echo Importing pgp key into gpg
gpg --fast-import /tmp/codesigning.asc

echo Deploy to maven central
mvn -P sign --settings travis-build/mvnsettings.xml deploy