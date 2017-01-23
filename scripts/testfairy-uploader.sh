#!/bin/sh

# Use this script for iOS or Android.
# Android apps must include the TestFairy SDK. Read more here: http://docs.testfairy.com/Android/Integrating_Android_SDK.html
# Android apps without SDK can be upladed with https://github.com/testfairy/command-line-uploader/blob/master/testfairy-upload-android-no-sdk.sh

UPLOADER_VERSION=2.1

# API key comes from env
# Put your TestFairy API_KEY here. Find it in your TestFairy account settings.
#TESTFAIRY_API_KEY=

# Tester Groups that will be notified when the app is ready. Setup groups in your TestFairy account testers page.
# This parameter is optional, leave empty if not required
TESTER_GROUPS=

# Should email testers about new version. Set to "off" to disable email notifications.
NOTIFY="off"

# If AUTO_UPDATE is "on" all users will be prompt to update to this build next time they run the app
AUTO_UPDATE="off"

# The maximum recording duration for every test. 
MAX_DURATION="24h"
#MAX_DURATION="24h"

# Metrics
METRICS="cpu,memory,network,network-requests,phone-signal,logcat,gps,battery,mic,wifi"

# Is video recording enabled for this build. valid values:  "on", "off", "wifi" 
VIDEO="wifi"

# Comment text will be included in the email sent to testers
#COMMIT_SHA1=`git rev-parse HEAD`
if [ ! -z "$TRAVIS" ]
then
	FULL_VERSION="${BASE_VERSION}.${VERSION_SUFFIX}+${TRAVIS_BUILD_NUMBER}"
else
	FULL_VERSION="${BASE_VERSION}.${VERSION_SUFFIX}+${COMMIT_SHA1}"
fi

# COMMIT_SHA1 is set in local-wrapper.bash
COMMENT="Version: ${FULL_VERSION}, GitHub commit: ${COMMIT_SHA1}"

# locations of various tools
CURL=curl
ZIP=zip
STAT=stat
DATE=date

SERVER_ENDPOINT=http://app.testfairy.com/api/upload

usage() {
	echo "Usage: testfairy-upload-ios.sh APP_FILENAME"
	echo
}
	
verify_tools() {

	# Windows users: this script requires curl. If not installed please get from http://cygwin.com/

	# Check 'curl' tool
	"${CURL}" --help >/dev/null
	if [ $? -ne 0 ]; then
		echo "Could not run curl tool, please check settings"
		exit 1
	fi
}

verify_settings() {
	if [ -z "${TESTFAIRY_API_KEY}" ]; then
		usage
		echo "Please update API_KEY with your private API key, as noted in the Settings page"
		exit 1
	fi
}


if [ $# -ne 1 ]; then
	usage
	exit 1
fi

# before even going on, make sure all tools work
verify_tools
verify_settings

APP_FILENAME=$1
if [ ! -f "${APP_FILENAME}" ]; then
	usage
	echo "Can't find file: ${APP_FILENAME}"
	exit 2
fi

# dSYM handling
echo "-- Compressing .dSYM dir from: $DSYM_PATH"
# DSYM_PATH comes from the environment
DWARF_DSYM_FILE_NAME=`basename $DSYM_PATH`
if [ "${DSYM_PATH}" == "" ] || [ "${DSYM_PATH}" == "/" ] || [ ! -d "${DSYM_PATH}" ]; then
	echo "-- Fatal: Can't find .dSYM folder!"
	exit 1
fi

NOW=$($DATE +%s)
ZIP_DSYM_FILENAME="/tmp/${NOW}-${DWARF_DSYM_FILE_NAME}.zip"

# Compress the .dSYM folder into a zip file
$ZIP -qrp9 "${ZIP_DSYM_FILENAME}" "${DSYM_PATH}"
FILE_SIZE=$($STAT -f "%z" "${ZIP_DSYM_FILENAME}")
echo "-- Compressed at: $ZIP_DSYM_FILENAME"

# temporary file paths
#DATE=`date`

/bin/echo "-- Uploading ${APP_FILENAME} to TestFairy... "

#CURL_CMD="${CURL} -v --progress-bar -s ${SERVER_ENDPOINT}/api/upload -F api_key=${TESTFAIRY_API_KEY} -F file=@${APP_FILENAME} -F video=${VIDEO} -F max-duration=${MAX_DURATION} -F comment=\"${COMMENT}\" -F auto-update=${AUTO_UPDATE} -F notify=${NOTIFY} -F instrumentation=off -F metrics=\"${METRICS}\" -A \"TestFairy iOS Command Line Uploader ${UPLOADER_VERSION}\""
#$($CURL_CMD)

echo "-- Curl command settings: "
echo "\tServer endpoint: $SERVER_ENDPOINT"
echo "\tIPA filename: $APP_FILENAME"
echo "\tCompressed .dSYM: $ZIP_DSYM_FILENAME"
echo "\tVideo: $VIDEO"
echo "\tMax duration: $MAX_DURATION"
echo "\tComment: $COMMENT"
echo "\tTesters groups: $TESTER_GROUPS"
echo "\tAuto update: $AUTO_UPDATE"
echo "\tNotify: $NOTIFY"
echo "\tMetrics: $METRICS\n"

# Original:
JSON=$( "${CURL}" -v --progress-bar -s ${SERVER_ENDPOINT} -F api_key=${TESTFAIRY_API_KEY} -F file="@${APP_FILENAME}" -F symbols_file="@${ZIP_DSYM_FILENAME}" -F video="${VIDEO}" -F max-duration="${MAX_DURATION}" -F comment="${COMMENT}" -F testers-groups="${TESTER_GROUPS}" -F auto-update="${AUTO_UPDATE}" -F notify="${NOTIFY}" -F instrumentation="off" -F metrics="${METRICS}" -A "TestFairy iOS Command Line Uploader ${UPLOADER_VERSION}" )

echo "-- curl response: $JSON"

rm -f $ZIP_DSYM_FILENAME

URL=$( echo ${JSON} | sed 's/\\\//\//g' | sed -n 's/.*"build_url"\s*:\s*"\([^"]*\)".*/\1/p' )
if [ -z "$URL" ]; then
	echo "FAILED!"
	echo
	echo "Build uploaded, but no reply from server. Please contact support@testfairy.com"
	exit 1
fi

echo "OK!"
echo
echo "Build was successfully uploaded to TestFairy and is available at:"
echo ${URL}

