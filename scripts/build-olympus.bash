#!/bin/bash
#
# Build Olympus after updating version string and deploy

# Various files that we edit
#SDK_COMMON_HEADER=RestCommClient/Classes/common.h
#OLYMPUS_UTILS=Examples/restcomm-olympus/restcomm-olympus/Utils.m
#OLYMPUS_PLIST=Examples/restcomm-olympus/restcomm-olympus/restcomm-olympus-Info.plist
#OLYMPUS_MAIN_ACTIVITY=Examples/restcomm-olympus/restcomm-olympus/AppDelegate.m

# source wait_with_output.sh so that we can access travis ci scripts to avoid stopping the build when no output is generated
. scripts/wait_with_output.sh

# For starters lets only create keychains in travis, since locally everything is setup already. But ultimately, we should create a separate new keychain locally to so that we can test that better
echo "-- TRAVIS variable value: $TRAVIS"

# Let's keep debug.keystore decryption and installation only for Travis. Locally we have a working keystore that might be confusing to update.
if [ ! -z "$TRAVIS" ]
then
	echo "-- Setting up signing"
	# We need the debug.keystore in order to be able to build a debug .apk
	echo "-- Decrypting keystore"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/keystore/${DEVELOPMENT_KEYSTORE}.enc -d -a -out scripts/certs/${DEVELOPMENT_KEYSTORE} || exit 1

	# We need global properties so that we get access to secret credentials
	echo "-- Decrypting and installing global gradle.properties"
	# DEBUG
	#ls scripts/configuration
	#echo "scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} || exit 1
  	cp scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} || exit 1

	echo "-- Installing keystore"
	# Overwrite default keystore file only in travis
	cp scripts/certs/${DEVELOPMENT_KEYSTORE} ~/.android/debug.keystore || exit 1
fi

if [ ! -z "$TRAVIS" ]
then
	export ORG_GRADLE_PROJECT_VERSION_CODE=$TRAVIS_BUILD_NUMBER
fi

if [ -z "$ORG_GRADLE_PROJECT_VERSION_CODE" ] || [ -z "$ORG_GRADLE_PROJECT_VERSION_NAME" ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_CODE and ORG_GRADLE_PROJECT_VERSION_NAME need to be set"
	exit 1
fi

echo -e "-- Using versionName: $ORG_GRADLE_PROJECT_VERSION_NAME"
echo -e "-- Using versionCode: $ORG_GRADLE_PROJECT_VERSION_CODE"

echo "-- Updating git commit hash for Olympus About screen"
#sed -i '' "s/#GIT-HASH/$COMMIT_SHA1/" $OLYMPUS_UTILS 

# Execute instrumented UI Tests
echo "-- Executing Olympus UI Tests"
if [ -z "$SKIP_OLYMPUS_UI_TESTS" ] || [[ "$SKIP_OLYMPUS_UI_TESTS" == "false" ]]
then
	./scripts/handle-ui-tests.bash
else
	echo "-- Skipping UI Tests."
fi

# Build and upload to TF
echo "-- Building Olympus and uploading to TestFairy"
if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
	cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x androidJavadocs -PtestfairyChangelog="Version: $ORG_GRADLE_PROJECT_VERSION_NAME+$ORG_GRADLE_PROJECT_VERSION_CODE, GitHub commit: $COMMIT_SHA1" testfairyDebug || exit 1
	if [ $? -ne 0 ]
	then
		echo "-- Failed to build Olympus for uploading to TestFairy."
		exit 1
	fi 
else
	echo "-- Skipping upload to Test Fairy."
	# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
	cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x androidJavadocs assemble || exit 1   # -PVERSION_CODE=$TRAVIS_BUILD_NUMBER -PVERSION_NAME=$VERSION_NAME
	if [ $? -ne 0 ]
	then
		echo "-- Failed to build Olympus for uploading to TestFairy."
		exit 1
	fi 
fi

cd ../.. || exit 1

# Clean up
#echo "-- Cleaning up"

#echo "-- Deintegrating olympus pods"

#echo "-- Rolling back changes in source files: $SDK_COMMON_HEADER, $OLYMPUS_UTILS, $OLYMPUS_PLIST"
#git checkout -- $SDK_COMMON_HEADER $OLYMPUS_UTILS $OLYMPUS_PLIST $OLYMPUS_APP_DELEGATE

#echo "-- Setting original keychain, \"$ORIGINAL_KEYCHAIN\", as default"
#security default-keychain -s $ORIGINAL_KEYCHAIN

#echo "-- Removing custom keychain $CUSTOM_KEYCHAIN"
#security delete-keychain $CUSTOM_KEYCHAIN

#echo "-- Removing keys, certs and profiles"
#rm scripts/certs/${DEVELOPMENT_CERT} 
