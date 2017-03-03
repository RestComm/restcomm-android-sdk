#!/bin/bash
#
# Build Olympus after updating version string and deploy

# Various files that we edit
#SDK_COMMON_HEADER=RestCommClient/Classes/common.h
#OLYMPUS_UTILS=Examples/restcomm-olympus/restcomm-olympus/Utils.m
#OLYMPUS_PLIST=Examples/restcomm-olympus/restcomm-olympus/restcomm-olympus-Info.plist
#OLYMPUS_MAIN_ACTIVITY=Examples/restcomm-olympus/restcomm-olympus/AppDelegate.m

# For starters lets only create keychains in travis, since locally everything is setup already. But ultimately, we should create a separate new keychain locally to so that we can test that better
echo "-- TRAVIS: $TRAVIS"

# Decrypting certs and profiles (not sure if profiles actually need to be encrypted, but this is how others did it so I'm following the same route just to be on the safe side)
echo "-- Setting up signing"
echo "-- Decrypting keys, etc"
echo "-- Setting up keychain"

echo "-- Installing provisioning profiles, so that XCode can find them"


if [ ! -z "$TRAVIS" ]
then
	export ORG_GRADLE_PROJECT_VERSION_CODE=$TRAVIS_BUILD_NUMBER
else
	export ORG_GRADLE_PROJECT_VERSION_CODE=1
fi

echo -e "-- Using versionName: $ORG_GRADLE_PROJECT_VERSION_NAME"
echo -e "-- Using versionCode: $ORG_GRADLE_PROJECT_VERSION_CODE"

echo "-- Updating git commit hash for Olympus About screen"
#sed -i '' "s/#GIT-HASH/$COMMIT_SHA1/" $OLYMPUS_UTILS 

# Build and upload to TF
echo "-- Building Olympus and uploading to TestFairy"
if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	cd Examples/restcomm-olympus && ./gradlew assemble   # -PVERSION_CODE=$TRAVIS_BUILD_NUMBER -PVERSION_NAME=$VERSION_NAME
	cd ../..
else
	echo "-- Skipping upload to Test Fairy."
	cd Examples/restcomm-olympus && ./gradlew -PtestfairyChangelog="Version: $ORG_GRADLE_PROJECT_VERSION_NAME+$ORG_GRADLE_PROJECT_VERSION_CODE, GitHub commit: $COMMIT_SHA1" testfairyDebug
	cd ../..
fi

# Clean up
echo "-- Cleaning up"

echo "-- Deintegrating olympus pods"

echo "-- Rolling back changes in source files: $SDK_COMMON_HEADER, $OLYMPUS_UTILS, $OLYMPUS_PLIST"
#git checkout -- $SDK_COMMON_HEADER $OLYMPUS_UTILS $OLYMPUS_PLIST $OLYMPUS_APP_DELEGATE

echo "-- Setting original keychain, \"$ORIGINAL_KEYCHAIN\", as default"
#security default-keychain -s $ORIGINAL_KEYCHAIN

echo "-- Removing custom keychain $CUSTOM_KEYCHAIN"
#security delete-keychain $CUSTOM_KEYCHAIN

echo "-- Removing keys, certs and profiles"
#rm scripts/certs/${DEVELOPMENT_CERT} 
