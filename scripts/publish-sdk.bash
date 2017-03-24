#!/bin/bash
#
# Publish SDK to maven repository

echo "-- Building SDK and uploading to maven repository"
cd restcomm.android.sdk && ./gradlew -PTRAVIS_BUILD=$TRAVIS_BUILD_NUMBER uploadArchives closeAndPromoteRepository
cd ..
