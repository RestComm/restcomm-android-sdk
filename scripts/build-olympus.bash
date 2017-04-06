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
echo "-- TRAVIS: $TRAVIS"

# Let's keep debug.keystore decryption and installation only for Travis. Locally we have a working keystore that might be confusing to update.
if [ ! -z "$TRAVIS" ]
then
	echo "-- Setting up signing"
	# We need the debug.keystore in order to be able to build a debug .apk
	echo "-- Decrypting keystore"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/keystore/${DEVELOPMENT_KEYSTORE}.enc -d -a -out scripts/certs/${DEVELOPMENT_KEYSTORE}

	# We need global properties so that we get access to secret credentials
	echo "-- Decrypting and installing global gradle.properties"
	ls scripts/configuration
	echo "scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}
  	cp scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} 

	echo "-- Installing keystore"
	# Overwrite default keystore file only in travis
	cp scripts/certs/${DEVELOPMENT_KEYSTORE} ~/.android/debug.keystore
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
	# Remember to specify the project (i.e. app)
	cd Examples/restcomm-olympus # && wait_with_output ./gradlew app:connectedAndroidTest 

	# Build App and Test .apk files (remember that for instrumented tests we need 2 apks one of the actual App under test and another for the testing logic)
	./gradlew -x signArchives assembleDebug assembleDebugAndroidTest

	# Decrypt firebase account key json
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${FIREBASE_ACCOUNT_KEY}.enc -d -a -out scripts/configuration/${FIREBASE_ACCOUNT_KEY}

	wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-149.0.0-linux-x86.tar.gz
	tar xf google-cloud-sdk-149.0.0-linux-x86.tar.gz -C /tmp/

	# I think this is no longer needed
	#echo "y" | /tmp/google-cloud-sdk/bin/gcloud components install beta

	# Activate google cloud account so that gcloud/gsutil commands that come later are already authenticated
	/tmp/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file scripts/configuration/${FIREBASE_ACCOUNT_KEY}

	# Run a test on Firebase test lab, and save the directory (i.e. google cloud bucket) where results will be stored in GCLOUD_DIR, so that we can copy them and check if everything went well after test is done
	# Here's how interesting output looks right now: Raw results will be stored in your GCS bucket at [https://console.developers.google.com/storage/browser/test-lab-tifnj30i214zc-jv3isjpmj42tu/2017-04-05_20:39:14.350799_OVsD/]
	export GCLOUD_DIR=`/tmp/google-cloud-sdk/bin/gcloud firebase test android run --async --type instrumentation --app app/build/outputs/apk/restcomm-olympus-1.0.0-BETA6+1-DEBUG.apk --test app/build/outputs/apk/app-debug-androidTest.apk --device-ids Nexus6 --os-version-ids 23 --locales en --orientations portrait --project emulator-2663d --results-bucket travis-ci-tests 2>&1 | tee /tmp/tee.txt | grep "Raw results will be stored in your GCS" | awk -F '[\\\[\\\]]' '{ print $2 }'`

	# Copy results from google cloud bucket locally so that we can observe them
	/tmp/google-cloud-sdk/bin/gsutil cp `/tmp/google-cloud-sdk/bin/gsutil ls gs://travis-ci-tests/**.xml` .
	retry_count=0
	resultsFound="false"

	while [ $retry_count -ne 20 ]
	do 
		echo "-- Checking google cloud (Firebase test lab) for test results..."
		GCLOUD_STATUS=`/tmp/google-cloud-sdk/bin/gsutil ls gs://travs-ci-tests/**.xml`
		if [ $? -eq 0  ]
		then
			$resultsFound="true"
			break;
		else 
			echo "-- Not generated yet"
			sleep 60
		fi
		let retry_count=$retry_count+1
	done

	if [[ $resultsFound == "true" ]]
	then
		resultsFilename=`basename $GCLOUD_STATUS`
		cat $resultsFilename
	else
		echo "-- Error: Report file couldn't be found in google cloud"
		# TODO: remove bucket data
		exit 1
	fi	

	# TODO: remove bucket data

	cd ../..
else
	echo "-- Skipping UI Tests."
fi

# Build and upload to TF
echo "-- Building Olympus and uploading to TestFairy"
if [ -z "$SKIP_TF_UPLOAD" ] || [[ "$SKIP_TF_UPLOAD" == "false" ]]
then
	# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
	cd Examples/restcomm-olympus && ./gradlew -x signArchives -PtestfairyChangelog="Version: $ORG_GRADLE_PROJECT_VERSION_NAME+$ORG_GRADLE_PROJECT_VERSION_CODE, GitHub commit: $COMMIT_SHA1" testfairyDebug
	cd ../..
else
	echo "-- Skipping upload to Test Fairy."
	# Skip the signArchives task until we properly setup Travis for signing + upload of archives to Sonatype. Otherwise the build breaks
	cd Examples/restcomm-olympus && ./gradlew -x signArchives assemble   # -PVERSION_CODE=$TRAVIS_BUILD_NUMBER -PVERSION_NAME=$VERSION_NAME
	cd ../..
fi

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
