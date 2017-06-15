#!/bin/bash
#
# Build Olympus and deploy to Test Fairy

echo
echo "== "
echo "== Building Olympus for upload to TestFairy"
echo "== "
echo

echo -e "-- Using versionName: $ORG_GRADLE_PROJECT_VERSION_NAME"
echo -e "-- Using versionCode: $ORG_GRADLE_PROJECT_VERSION_CODE"

if [ -z $ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE environment variable missing"
	exit 1
fi

echo "-- Building Olympus & uploading to TF -this might take some time..."
if [ -z $TESTFAIRY_APP_TOKEN ]
then
	echo "-- Error: TESTFAIRY_APP_TOKEN environment variable missing"
	exit 1
fi

if [ -z $ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY environment variable missing"
	exit 1
fi

# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x uploadArchives -x androidJavadocs -PtestfairyChangelog="Version: $ORG_GRADLE_PROJECT_VERSION_NAME+$ORG_GRADLE_PROJECT_VERSION_CODE, GitHub commit: $COMMIT_SHA1" testfairyDebug || exit 1
if [ $? -ne 0 ]
then
	echo "-- Failed to build Olympus for uploading to TestFairy."
	exit 1
fi 

# Keep the code to make a plain build of Olympus without upload to TF in case we need it in the future:
#else
#	echo "-- Building Olympus (no TF upload)."
#	# Skip the signArchives & Javadoc generation tasks since we don't want them here
#	cd Examples/restcomm-olympus && ./gradlew --quiet -x signArchives -x uploadArchives -x androidJavadocs assemble || exit 1   # -PVERSION_CODE=$TRAVIS_BUILD_NUMBER -PVERSION_NAME=$VERSION_NAME
#	if [ $? -ne 0 ]
#	then
#		echo "-- Failed to build Olympus."
#		exit 1
#	fi 
#fi

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
