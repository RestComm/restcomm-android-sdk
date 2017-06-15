#!/bin/bash
#
# Build test .apks, upload to Firebase Test Lab, execute tests, and get results

# Wait interval for when waiting for test to complete
export WAIT_INTERVAL=60

echo
echo "== "
echo "== Executing UI Tests"
echo "== "
echo

# source wait_with_output.sh so that we can access travis ci scripts to avoid stopping the build when no output is generated
#. scripts/wait_with_output.sh

# Remember to specify the project (i.e. app). We no longer run connectedAndroidTest due to emulator performance in Travis, but let's keep the command commented out as it might come in handy
cd Examples/restcomm-olympus || exit 1 # && wait_with_output ./gradlew app:connectedAndroidTest 

# Build App and Test .apk files (remember that for instrumented tests we need 2 apks one of the actual App under test and another for the testing logic)
echo "-- Build App and Test .apk files"
./gradlew --quiet -x androidJavadocs -x signArchives -x uploadArchives assembleDebug assembleDebugAndroidTest
if [ $? -ne 0 ]
then
	echo "-- Failed to build Olympus for UI tests."
	exit 1
fi 

echo "-- Downloading google cloud sdk"
# use --no-verbose to avoid excessive output
wget --no-verbose -O /tmp/google-cloud-sdk.tar.gz https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-149.0.0-linux-x86.tar.gz || exit 1
tar xf /tmp/google-cloud-sdk.tar.gz -C /tmp/ || exit 1

# I think this is no longer needed
#echo "y" | /tmp/google-cloud-sdk/bin/gcloud components install beta

# Decrypt firebase account key json
openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}.enc -d -a -out ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY} || exit 1

# Activate google cloud account so that gcloud/gsutil commands that come later are already authenticated
echo "-- Activating google cloud account"
/tmp/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY} || exit 1

# Run a test on Firebase test lab, and save the directory (i.e. google cloud bucket) where results will be stored in variable GCLOUD_RAW_RESULT_FULL_URL, so that we can copy them and check if everything went well after test is done
echo "-- Uploading .apks on Firebase Test Lab and starting test, this might take some time depending on your connection"

APP_APK_PATH="app/build/outputs/apk/restcomm-olympus-${ORG_GRADLE_PROJECT_VERSION_NAME}+${ORG_GRADLE_PROJECT_VERSION_CODE}-DEBUG.apk"
TEST_APK_PATH="app/build/outputs/apk/app-debug-androidTest.apk"

echo -e "\t> Using App apk: $APP_APK_PATH"
echo -e "\t> Using test apk: $TEST_APK_PATH"

# Here's how interesting output looks right now prior to grep & awk: 'Raw results will be stored in your GCS bucket at [https://console.developers.google.com/storage/browser/test-lab-tifnj30i214zc-jv3isjpmj42tu/2017-04-05_20:39:14.350799_OVsD/]'. Notice that awk command just retrieves the URL between [ and ] chars
export GCLOUD_RAW_RESULT_FULL_URL=`/tmp/google-cloud-sdk/bin/gcloud firebase test android run --async --type instrumentation --app $APP_APK_PATH --test $TEST_APK_PATH --device-ids Nexus6 --os-version-ids 23 --locales en --orientations portrait --project emulator-2663d 2>&1 | tee -a /tmp/ci-job-output.txt | grep "Raw results will be stored in your GCS" | awk -F '[\\\[\\\]]' '{ print $2 }'`
#export GCLOUD_RAW_RESULT_FULL_URL=`/tmp/google-cloud-sdk/bin/gcloud firebase test android run --async --type instrumentation --app $APP_APK_PATH --test $TEST_APK_PATH --device-ids Nexus6 --os-version-ids 23 --locales en --orientations portrait --project emulator-2663d --results-bucket travis-ci-tests 2>&1 | tee -a /tmp/ci-job-output.txt | grep "Raw results will be stored in your GCS" | awk -F '[\\\[\\\]]' '{ print $2 }'`

if [ $? -ne 0  ]
then
	echo "-- Failed to upload .apks to Firebase Test lab -bailing"
	exit 1
fi

echo "-- Successfully uploaded .apks to Firebase Test lab and started tests"

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
	GCLOUD_REPORT_PATH=`/tmp/google-cloud-sdk/bin/gsutil ls gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}/**.xml`
	if [ $? -eq 0  ]
	then
		resultsFound="true"
		break;
	else
		echo "-- Results not generated yet (test still running), retry count: $retry_count -will try again after $WAIT_INTERVAL" seconds
		sleep $WAIT_INTERVAL
	fi
	let "retry_count=retry_count+1"
done

if [[ "$resultsFound" == "true" ]]
then
	echo "-- Report successfully retrieved"
else
	echo "-- Error: Report file couldn't be found in google cloud. This usually means that the test didn't finish on time -bailing"
	#echo "-- Removing google cloud bucket data before bail"
	#/tmp/google-cloud-sdk/bin/gsutil -m rm -r gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}/
	exit 1
fi

# Copy results from google cloud bucket locally so that we can observe them
echo "-- Copying results from google cloud locally"
/tmp/google-cloud-sdk/bin/gsutil cp $GCLOUD_REPORT_PATH /tmp/ || exit 1

resultsFilename=`basename $GCLOUD_REPORT_PATH`
echo "-- Printing test report from $resultsFilename:"
echo "<<<<<<<<<<"
cat /tmp/$resultsFilename
echo 
echo ">>>>>>>>>>"

# match 'testsuite' anywhere and return value of attribute errors
errorTestCount=`xmlstarlet sel -t -m //testsuite -v @errors /tmp/${resultsFilename}`
failedTestCount=`xmlstarlet sel -t -m //testsuite -v @failures /tmp/${resultsFilename}`
if [[ $errorTestCount -ne 0 || $failedTestCount -ne 0 ]]
then
	echo "-- Error: At least one test case encountered an error or failure -bailing"
	exit 1
fi

echo "-- Awesome, UI tests are successful"

echo "-- Cleaning up"
# Remove bucket data, since reports and everything can be found in Firebase Console, no need to charged for google cloud storage
#echo "-- Removing google cloud bucket data"
#/tmp/google-cloud-sdk/bin/gsutil -m rm -r gs://${GCLOUD_RAW_RESULT_PROJECT_RELATIVE_PATH}

rm -fr /tmp/google-cloud-sdk* ../../scripts/configuration/${FIREBASE_ACCOUNT_KEY}

cd ../.. || exit 1 

