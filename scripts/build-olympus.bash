#!/bin/bash
#
# Build Olympus after updating version string and deploy

PLIST_BUDDY="/usr/libexec/PlistBuddy"
# Various files that we edit
SDK_COMMON_HEADER=RestCommClient/Classes/common.h
OLYMPUS_UTILS=Examples/restcomm-olympus/restcomm-olympus/Utils.m
OLYMPUS_PLIST=Examples/restcomm-olympus/restcomm-olympus/restcomm-olympus-Info.plist
OLYMPUS_MAIN_ACTIVITY=Examples/restcomm-olympus/restcomm-olympus/AppDelegate.m

echo "-- Installing CocoaPod dependencies"

# For starters lets only create keychains in travis, since locally everything is setup already. But ultimately, we should create a separate new keychain locally to so that we can test that better
echo "-- TRAVIS: $TRAVIS"

# Decrypting certs and profiles (not sure if profiles actually need to be encrypted, but this is how others did it so I'm following the same route just to be on the safe side)
echo "-- Setting up signing"
echo "-- Decrypting keys, etc"
echo "-- Setting up keychain"

echo "-- Installing provisioning profiles, so that XCode can find them"


if [ ! -z "$TRAVIS" ]
then
	# Travis
	BUNDLE_VERSION="${VERSION_SUFFIX}+${TRAVIS_BUILD_NUMBER}"
else
	# Local, let's use the commit hash for now
	BUNDLE_VERSION="${VERSION_SUFFIX}+${COMMIT_SHA1}"
fi

echo -e "-- Updating .plist version strings:\n\tCFBundleShortVersionString $BASE_VERSION\n\tCFBundleVersion ${BUNDLE_VERSION}"
# Set base version
$PLIST_BUDDY -c "Set :CFBundleShortVersionString $BASE_VERSION" "$OLYMPUS_PLIST"
$PLIST_BUDDY -c "Set :CFBundleVersion ${BUNDLE_VERSION}" "$OLYMPUS_PLIST"
# Set suffix

# Update build string in sources if needed
echo "-- Updating Sofia SIP User Agent with version"
#sed -i '' "s/#BASE_VERSION/$BASE_VERSION/" $SDK_COMMON_HEADER 
#sed -i '' "s/#VERSION_SUFFIX/$VERSION_SUFFIX/" $SDK_COMMON_HEADER 
if [ ! -z "$TRAVIS" ]
then
#	sed -i '' "s/#BUILD/$TRAVIS_BUILD_NUMBER/" $SDK_COMMON_HEADER
else
#	sed -i '' "s/#BUILD/$COMMIT_SHA1/" $SDK_COMMON_HEADER
fi

echo "-- Updating git commit hash for Olympus About screen"
#sed -i '' "s/#GIT-HASH/$COMMIT_SHA1/" $OLYMPUS_UTILS 

echo "-- Updating Test Fairy App Key, so that we get TF stats and insights"
# uncomment token line
sed -i '' '/#TESTFAIRY_APP_TOKEN/s/\/\///' $OLYMPUS_APP_DELEGATE
# replace placeholder with actual app token
sed -i '' "s/#TESTFAIRY_APP_TOKEN/$TESTFAIRY_APP_TOKEN/" $OLYMPUS_APP_DELEGATE 

# Build and sign with development certificate (cannot use distribution cert here!)
echo "-- Building Olympus"

# Upload to Test Fairy
if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	# export path so that it is available inside testfairy-uploader.sh script
	#export DSYM_PATH=build/Products/restcomm-olympus.xcarchive/dSYMs/restcomm-olympus.app.dSYM

	echo "-- Uploading .ipa and .dSYM to TestFairy"
	#scripts/testfairy-uploader.sh build/Products/IPA/restcomm-olympus.ipa 

	#echo "-- Uploading dSYM to TestFairy"
	#scripts/upload-dsym-testfairy.sh -d $TESTFAIRY_API_KEY -p build/Products/restcomm-olympus.xcarchive/dSYMs/restcomm-olympus.app.dSYM
else
	echo "-- Skipping upload to Test Fairy."
fi

# Clean up
echo "-- Cleaning up"

echo "-- Deintegrating olympus pods"

echo "-- Rolling back changes in source files: $SDK_COMMON_HEADER, $OLYMPUS_UTILS, $OLYMPUS_PLIST"
git checkout -- $SDK_COMMON_HEADER $OLYMPUS_UTILS $OLYMPUS_PLIST $OLYMPUS_APP_DELEGATE

echo "-- Setting original keychain, \"$ORIGINAL_KEYCHAIN\", as default"
security default-keychain -s $ORIGINAL_KEYCHAIN

echo "-- Removing custom keychain $CUSTOM_KEYCHAIN"
security delete-keychain $CUSTOM_KEYCHAIN

echo "-- Removing keys, certs and profiles"
rm scripts/certs/${DEVELOPMENT_CERT} \
	scripts/certs/${DEVELOPMENT_KEY} \
	scripts/certs/${DISTRIBUTION_CERT} \
	scripts/certs/${DISTRIBUTION_KEY} \
	./scripts/provisioning-profile/$DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME.mobileprovision \
	./scripts/provisioning-profile/${DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision \
	~/Library/MobileDevice/Provisioning\ Profiles/${DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision \
	~/Library/MobileDevice/Provisioning\ Profiles/${DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision
