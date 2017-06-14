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

echo
echo "== "
echo "== Olympus related handling"
echo "== "
echo

# Need keystore file to be able to sign the .apk. Let's keep debug.keystore decryption and installation only for Travis. Locally we have a working keystore that might be confusing to update.
if [ ! -z "$TRAVIS" ]
then
	echo "-- Setting up signing"
	# We need the debug.keystore in order to be able to build a debug .apk
	echo "-- Decrypting signing keystore"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/keystore/${DEVELOPMENT_KEYSTORE}.enc -d -a -out scripts/certs/${DEVELOPMENT_KEYSTORE} || exit 1

	echo "-- Installing keystore"
	# Overwrite default keystore file only in travis
	cp scripts/certs/${DEVELOPMENT_KEYSTORE} ~/.android/${DEVELOPMENT_KEYSTORE} || exit 1
fi

# Build number corresponds to Android version code
export ORG_GRADLE_PROJECT_VERSION_CODE=$ORG_GRADLE_PROJECT_BUILD_NUMBER

if [ -z "$ORG_GRADLE_PROJECT_VERSION_CODE" ] || [ -z "$ORG_GRADLE_PROJECT_VERSION_NAME" ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_CODE and ORG_GRADLE_PROJECT_VERSION_NAME need to be set"
	exit 1
fi

echo -e "-- Using versionName: $ORG_GRADLE_PROJECT_VERSION_NAME"
echo -e "-- Using versionCode: $ORG_GRADLE_PROJECT_VERSION_CODE"

# Execute instrumented UI Tests
if [ -z "$SKIP_OLYMPUS_UI_TESTS" ] || [[ "$SKIP_OLYMPUS_UI_TESTS" == "false" ]]
then
	if ! ./scripts/handle-ui-tests.bash
	then
		exit 1
	fi
else
	echo "-- Skipping UI Tests."
fi

# Build and upload to TF
echo
echo "== "
echo "== Building Olympus and (potentially) uploading to TestFairy"
echo "== "
echo

if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	echo "-- Building Olympus & uploading to TF -this might take some time..."
	# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
	cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x uploadArchives -x androidJavadocs -PtestfairyChangelog="Version: $ORG_GRADLE_PROJECT_VERSION_NAME+$ORG_GRADLE_PROJECT_VERSION_CODE, GitHub commit: $COMMIT_SHA1" testfairyDebug || exit 1
	if [ $? -ne 0 ]
	then
		echo "-- Failed to build Olympus for uploading to TestFairy."
		exit 1
	fi 
else
	echo "-- Building Olympus (no TF upload)."
	# Skip the signArchives & Javadoc generation tasks since we don't want them here
	cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x uploadArchives -x androidJavadocs assemble || exit 1   # -PVERSION_CODE=$TRAVIS_BUILD_NUMBER -PVERSION_NAME=$VERSION_NAME
	if [ $? -ne 0 ]
	then
		echo "-- Failed to build Olympus."
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
