#!/bin/sh

TESTFAIRY_ENDPOINT="http://app.testfairy.com/upload/dsym/"

ZIP=zip
#CURL=curl
STAT=stat
DATE=date

help() {
	echo "Usage: ${0} [-f] TESTFAIRY_API_KEY [-p DSYM_PATH]"
	exit 1
}

foreground_upload() {
	# Upload zipped .dSYM file to TestFairy's servers
	echo "Uploading in the foreground: ${1}"
	curl -v --progress-bar -s -F api_key="${API_KEY}" -F dsym=@"${1}" "${TESTFAIRY_ENDPOINT}"
	echo "Symbols uploaded"

	# Clean up behind
	rm -f ${1}
}

API_KEY=$TESTFAIRY_API_KEY
if [ ! "${API_KEY}" ]; then
	help
fi

DWARF_DSYM_FILE_NAME=`basename $DSYM_PATH`
if [ "${DSYM_PATH}" == "" ] || [ "${DSYM_PATH}" == "/" ] || [ ! -d "${DSYM_PATH}" ]; then
	echo "Fatal: Can't find .dSYM folder!"
	help
fi

NOW=$($DATE +%s)
TMP_FILENAME="/tmp/${NOW}-${DWARF_DSYM_FILE_NAME}.zip"

# Compress the .dSYM folder into a zip file
echo "-- Compressing .dSYM folder ${DSYM_PATH}"
$ZIP -qrp9 "${TMP_FILENAME}" "${DSYM_PATH}"
FILE_SIZE=$($STAT -f "%z" "${TMP_FILENAME}")
echo "-- Compressed at: $TMP_FILENAME"

echo "Uploading ${FILE_SIZE} bytes to dsym server in foreground"
foreground_upload "${TMP_FILENAME}"

