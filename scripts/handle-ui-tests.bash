#!/bin/bash
#
# Build test .apks, upload to Firebase Test Lab execute tests and get results

# Remember to specify the project (i.e. app)
cd Examples/restcomm-olympus # && wait_with_output ./gradlew app:connectedAndroidTest 

# Build App and Test .apk files (remember that for instrumented tests we need 2 apks one of the actual App under test and another for the testing logic)
echo "-- Build App and Test .apk files"
./gradlew -x signArchives assembleDebug assembleDebugAndroidTest

echo "-- Downloading google cloud sdk"
wget -O /tmp/google-cloud-sdk.tar.gz https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-149.0.0-linux-x86.tar.gz
tar xf /tmp/google-cloud-sdk.tar.gz -C /tmp/

# I think this is no longer needed
#echo "y" | /tmp/google-cloud-sdk/bin/gcloud components install beta

# Decrypt firebase account key json
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}.enc -d -a -out ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}

# Activate google cloud account so that gcloud/gsutil commands that come later are already authenticated
echo "-- Activating google cloud account"
/tmp/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}

# Run a test on Firebase test lab, and save the directory (i.e. google cloud bucket) where results will be stored in variable GCLOUD_RAW_RESULT_FULL_URL, so that we can copy them and check if everything went well after test is done
echo "-- Uploading .apks on Firebase Test Lab and starting test on Firebase Test Lab"

APP_APK_PATH="app/build/outputs/apk/restcomm-olympus-${VERSION_NAME}+${VERSION_CODE}-DEBUG.apk"
TEST_APK_PATH="app/build/outputs/apk/app-debug-androidTest.apk"

echo -e "\t> Using App apk: $APP_APK_PATH"
echo -e "\t> Using test apk: $TEST_APK_PATH"

# Here's how interesting output looks right now prior to grep & awk: 'Raw results will be stored in your GCS bucket at [https://console.developers.google.com/storage/browser/test-lab-tifnj30i214zc-jv3isjpmj42tu/2017-04-05_20:39:14.350799_OVsD/]'. Notice that awk command just retrieves the URL between [ and ] chars
export GCLOUD_RAW_RESULT_FULL_URL=`/tmp/google-cloud-sdk/bin/gcloud firebase test android run --async --type instrumentation --app $APP_APK_PATH --test $TEST_APK_PATH --device-ids Nexus6 --os-version-ids 23 --locales en --orientations portrait --project emulator-2663d 2>&1 | tee -a /tmp/ci-job-output.txt | grep "Raw results will be stored in your GCS" | awk -F '[\\\[\\\]]' '{ print $2 }'`
#export GCLOUD_RAW_RESULT_FULL_URL=`/tmp/google-cloud-sdk/bin/gcloud firebase test android run --async --type instrumentation --app $APP_APK_PATH --test $TEST_APK_PATH --device-ids Nexus6 --os-version-ids 23 --locales en --orientations portrait --project emulator-2663d --results-bucket travis-ci-tests 2>&1 | tee -a /tmp/ci-job-output.txt | grep "Raw results will be stored in your GCS" | awk -F '[\\\[\\\]]' '{ print $2 }'`

# if GCLOUD_RAW_RESULT_FULL_URL is 'https://console.developers.google.com/storage/browser/travis-ci-tests/2017-04-06_12:13:28.941220_PpTv/', then 
# GCLOUD_RELATIVE_URL will be 'travis-ci-tests/2017-04-06_12:13:28.941220_PpTv'
export GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH=`echo $GCLOUD_RAW_RESULT_FULL_URL | rev | cut -d'/' -f2-3 | rev`
echo "-- GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH: $GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH"

export GCLOUD_RAW_RESULT_PROJECT_BUCKET=`echo $GCLOUD_RAW_RESULT_FULL_URL | rev | cut -d'/' -f3 | rev`
echo "-- GCLOUD_RAW_RESULT_PROJECT_BUCKET: $GCLOUD_RAW_RESULT_PROJECT_BUCKET"

retry_count=0
resultsFound="false"

echo "-- Checking google cloud (Firebase test lab) for test results..."
while [ $retry_count -lt 20 ]
do
	GCLOUD_STATUS=`/tmp/google-cloud-sdk/bin/gsutil ls gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}/**.xml`
	if [ $? -eq 0  ]
	then
		resultsFound="true"
		break;
	else
		echo "-- Not generated yet, count: $retry_count -retrying"
		sleep 60
	fi
	let "retry_count=retry_count+1"
done

if [[ "$resultsFound" == "true" ]]
then
	echo "-- Results generated!"
else
	echo "-- Error: Report file couldn't be found in google cloud, bailing"
	echo "-- Removing google cloud bucket data before bail"
	/tmp/google-cloud-sdk/bin/gsutil -m rm -r gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}/
	exit 1
fi

# Copy results from google cloud bucket locally so that we can observe them
echo "-- Copying results from google cloud locally"
/tmp/google-cloud-sdk/bin/gsutil cp `/tmp/google-cloud-sdk/bin/gsutil ls gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}/**.xml` .

resultsFilename=`basename $GCLOUD_STATUS`
echo "-- Printing test results from $resultsFilename:"
cat $resultsFilename

echo "-- Cleaning up"
# Remove bucket data, since reports and everything can be found in Firebase Console, no need to charged for google cloud storage
echo "-- Removing google cloud bucket data"
#/tmp/google-cloud-sdk/bin/gsutil -m rm -r gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}

rm -fr /tmp/google-cloud-sdk* ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}

cd ../..

