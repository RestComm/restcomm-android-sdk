#!/bin/bash
#
# Build Olympus after updating version string and deploy

PLIST_BUDDY="/usr/libexec/PlistBuddy"
# Various files that we edit
SDK_COMMON_HEADER=RestCommClient/Classes/common.h
OLYMPUS_UTILS=Examples/restcomm-olympus/restcomm-olympus/Utils.m
OLYMPUS_PLIST=Examples/restcomm-olympus/restcomm-olympus/restcomm-olympus-Info.plist
OLYMPUS_APP_DELEGATE=Examples/restcomm-olympus/restcomm-olympus/AppDelegate.m

echo "-- Installing CocoaPod dependencies"
pod install --project-directory=Examples/restcomm-olympus

# For starters lets only create keychains in travis, since locally everything is setup already. But ultimately, we should create a separate new keychain locally to so that we can test that better
echo "-- TRAVIS: $TRAVIS"

# Decrypting certs and profiles (not sure if profiles actually need to be encrypted, but this is how others did it so I'm following the same route just to be on the safe side)
echo "-- Setting up signing"
echo "-- Decrypting keys, etc"
# Development
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/certs/${DEVELOPMENT_CERT}.enc -d -a -out scripts/certs/${DEVELOPMENT_CERT}
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/certs/${DEVELOPMENT_KEY}.enc -d -a -out scripts/certs/${DEVELOPMENT_KEY}

# Olympus provisioning profile
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/provisioning-profile/${DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision.enc -d -a -out scripts/provisioning-profile/${DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision

# Distribution
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/certs/${DISTRIBUTION_CERT}.enc -d -a -out scripts/certs/${DISTRIBUTION_CERT}
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/certs/${DISTRIBUTION_KEY}.enc -d -a -out scripts/certs/${DISTRIBUTION_KEY}

openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/provisioning-profile/${DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision.enc -d -a -out scripts/provisioning-profile/${DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME}.mobileprovision

echo "-- Setting up keychain"
ORIGINAL_KEYCHAIN=`security default-keychain | rev | cut -d '/' -f -1 | sed 's/\"//' | rev`
echo "-- Original keychain: \"$ORIGINAL_KEYCHAIN\""
if [[ "$ORIGINAL_KEYCHAIN" == "$CUSTOM_KEYCHAIN" ]]
then
		echo "-- Custom keychain already set as default, bailing out to avoid issues."
		exit 1
fi

echo "-- Creating custom keychain for signing: \"$CUSTOM_KEYCHAIN\""
# Create a custom keychain, $CUSTOM_KEYCHAIN using passwordk $CUSTOM_KEYCHAIN_PASSWORD
security create-keychain -p $CUSTOM_KEYCHAIN_PASSWORD $CUSTOM_KEYCHAIN

echo "-- Setting up $CUSTOM_KEYCHAIN as default"
# Make the $CUSTOM_KEYCHAIN default, so xcodebuild will use it for signing
security default-keychain -s $CUSTOM_KEYCHAIN

# Unlock the keychain
security unlock-keychain -p $CUSTOM_KEYCHAIN_PASSWORD $CUSTOM_KEYCHAIN

# Set keychain timeout to 1 hour for long builds
# see http://www.egeek.me/2013/02/23/jenkins-and-xcode-user-interaction-is-not-allowed/
security set-keychain-settings -t 3600 -l ~/Library/Keychains/$CUSTOM_KEYCHAIN


echo "-- Showing keychain info"
security show-keychain-info $CUSTOM_KEYCHAIN

# Add certificates to keychain and allow codesign to access them
#security import ./scripts/certs/${APPLE_CERT} -k $CUSTOM_KEYCHAIN -T /usr/bin/codesign -A
security import ./scripts/certs/${APPLE_CERT} -k $CUSTOM_KEYCHAIN -A
# Development
security import ./scripts/certs/${DEVELOPMENT_CERT} -k $CUSTOM_KEYCHAIN -A
security import ./scripts/certs/${DEVELOPMENT_KEY} -k $CUSTOM_KEYCHAIN -P $PRIVATE_KEY_PASSWORD -A

# Distribution
security import ./scripts/certs/${DISTRIBUTION_CERT} -k $CUSTOM_KEYCHAIN -A
security import ./scripts/certs/${DISTRIBUTION_KEY} -k $CUSTOM_KEYCHAIN -P $PRIVATE_KEY_PASSWORD -A

# Fix for OS X Sierra that hungs in the codesign step due to a UI prompt not visible to headless servers
echo "-- Updating partition IDs for certs in the custom keychain, to avoid codesign hanging, waiting for UI input"
security set-key-partition-list -S apple-tool:,apple: -s -k $CUSTOM_KEYCHAIN_PASSWORD $CUSTOM_KEYCHAIN > /dev/null

echo "-- Installing provisioning profiles, so that XCode can find them"
#echo "Checking scripts"
#find scripts
# Put the provisioning profile in the right place so that they are picked up by Xcode
mkdir -p ~/Library/MobileDevice/Provisioning\ Profiles
cp "./scripts/provisioning-profile/$DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME.mobileprovision" ~/Library/MobileDevice/Provisioning\ Profiles/
cp "./scripts/provisioning-profile/$DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME.mobileprovision" ~/Library/MobileDevice/Provisioning\ Profiles/

echo "-- Checking provisioning profiles"
#find scripts
ls -al ~/Library/MobileDevice/Provisioning\ Profiles/
echo "Checking signing identities: "
security find-identity -p codesigning -v


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
if [ ! -f $SDK_COMMON_HEADER ]; then
	echo "$SDK_COMMON_HEADER not found, bailing"
	exit 1;
fi
sed -i '' "s/#BASE_VERSION/$BASE_VERSION/" $SDK_COMMON_HEADER 
sed -i '' "s/#VERSION_SUFFIX/$VERSION_SUFFIX/" $SDK_COMMON_HEADER 
if [ ! -z "$TRAVIS" ]
then
	sed -i '' "s/#BUILD/$TRAVIS_BUILD_NUMBER/" $SDK_COMMON_HEADER
else
	sed -i '' "s/#BUILD/$COMMIT_SHA1/" $SDK_COMMON_HEADER
fi

echo "-- Updating git commit hash for Olympus About screen"
if [ ! -f $OLYMPUS_UTILS ]; then
	echo "$OLYMPUS_UTILS not found, bailing"
	exit 1;
fi
sed -i '' "s/#GIT-HASH/$COMMIT_SHA1/" $OLYMPUS_UTILS 

echo "-- Updating Test Fairy App Key, so that we get TF stats and insights"
if [ ! -f $OLYMPUS_APP_DELEGATE ]; then
	echo "$OLYMPUS_APP_DELEGATE not found, bailing"
	exit 1;
fi
# uncomment token line
sed -i '' '/#TESTFAIRY_APP_TOKEN/s/\/\///' $OLYMPUS_APP_DELEGATE
# replace placeholder with actual app token
sed -i '' "s/#TESTFAIRY_APP_TOKEN/$TESTFAIRY_APP_TOKEN/" $OLYMPUS_APP_DELEGATE 

# Build and sign with development certificate (cannot use distribution cert here!)
echo "-- Building Olympus"
xcodebuild archive -workspace Examples/restcomm-olympus/restcomm-olympus.xcworkspace -scheme restcomm-olympus -configuration Release  -derivedDataPath ./build  -archivePath ./build/Products/restcomm-olympus.xcarchive | xcpretty

# Exporting and signing with distribution certificate
echo "-- Exporting Archive"
# IMPORTANT: Use xcodebuild wrapper that sets up rvm to workaround the "No applicable devices found" issue 
scripts/xcodebuild-rvm.bash -exportArchive -archivePath ./build/Products/restcomm-olympus.xcarchive -exportOptionsPlist ./scripts/exportOptions-Enterprise.plist -exportPath ./build/Products/IPA | xcpretty


# Upload to Test Fairy
if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	# export path so that it is available inside testfairy-uploader.sh script
	export DSYM_PATH=build/Products/restcomm-olympus.xcarchive/dSYMs/restcomm-olympus.app.dSYM

	echo "-- Uploading .ipa and .dSYM to TestFairy"
	scripts/testfairy-uploader.sh build/Products/IPA/restcomm-olympus.ipa 

	#echo "-- Uploading dSYM to TestFairy"
	#scripts/upload-dsym-testfairy.sh -d $TESTFAIRY_API_KEY -p build/Products/restcomm-olympus.xcarchive/dSYMs/restcomm-olympus.app.dSYM
else
	echo "-- Skipping upload to Test Fairy."
fi

# Clean up
echo "-- Cleaning up"

echo "-- Deintegrating olympus pods"
cd Examples/restcomm-olympus && pod deintegrate
cd ../..

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
