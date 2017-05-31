#!/bin/bash
#
# Publish SDK to maven repository

echo
echo "== "
echo "== Publishing SDK"
echo "== "
echo

echo "-- Building SDK and uploading to maven repository"
# remember that when putting more than 1 task in the command line, gradle makes sure that the are not ran more than one time. Also,
# notice that ORG_GRADLE_PROJECT_BUILD_NUMBER is automatically passed as a property in gragle to provide the Build Number
cd restcomm.android.sdk && ./gradlew --quiet -x androidJavadocs uploadArchives closeAndPromoteRepository || exit 1
cd .. || exit 1
