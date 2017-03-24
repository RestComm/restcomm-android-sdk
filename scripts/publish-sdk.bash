#!/bin/bash
#
# Publish SDK to maven repository

echo "-- Building SDK and uploading to maven repository"
# remember that when putting more than 1 task in the command line, gladle makes sure that the are not ran more than one time
cd restcomm.android.sdk && ./gradlew -PTRAVIS_BUILD=$TRAVIS_BUILD_NUMBER uploadArchives closeAndPromoteRepository
cd ..
