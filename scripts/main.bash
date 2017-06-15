#!/bin/bash
#
# Main script that will drive CI/CD actions, depending on type of commit.

echo
echo "== "
echo "== Processing main script."
echo "== "
echo

# TODO: Run integration tests - TODO: take this out to a separate script
#echo "-- Running Integration Tests ."
#if [ -z "$SKIP_INTEGRATION_TESTS" ] || [[ "$SKIP_INTEGRATION_TESTS" == "false" ]]
#then
#	# TODO: this should become a single line both for local and travis builds
#	if [ ! -z "$TRAVIS" ]
#	then
#		#set -o pipefail && travis_retry xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE,OS=10.0' | xcpretty
#		#xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE'
#		# TODO: add travis IT
#	else
#		# For local builds don't specify iOS version, to make it more flexible
#		#xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE' | xcpretty
#		# TODO: add local IT
#	fi
#else
#	echo "-- Skipping Integration Tests."
#fi

#if [ ! -z "$TRAVIS" ]
#then
	# This is a travis build
#	if [[ "$TRAVIS_PULL_REQUEST" == "true" ]]; then
#		echo "-- This is a pull request, bailing out."
#		exit 0
#	fi

	# CURRENT_BRANCH is the brach we are passing from the travis CI settings and shows which branch CI should deploy from
	#if [[ "$TRAVIS_BRANCH" != "$CURRENT_BRANCH" ]]; then
	#	echo "-- Testing on a branch other than $CURRENT_BRANCH, bailing out."
	#	exit 0
	#fi
#fi

if ! ./scripts/setup-prerequisites.bash
then
	exit 1
fi

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

# Update reference documentation (trusted build only due to need to have write privileges to GitHub upstream repo)
if [ "$CURRENT_BRANCH" == $RELEASE_BRANCH ] && [[ -z "$SKIP_DOC_GENERATION" || "$SKIP_DOC_GENERATION" == "false" ]]
then
	if [ -z $TRUSTED_BUILD ]
	then
		echo "-- Cannot generate doc in an untrusted build, skipping"
	else
		if ! ./scripts/update-doc.bash
		then
			exit 1
		fi
	fi

else
	echo "-- Skipping Documentation Generation."
fi

# Build SDK and publish to maven repo (trusted build)
if [ "$CURRENT_BRANCH" == $RELEASE_BRANCH ] && [[ -z "$SKIP_SDK_PUBLISH_TO_MAVEN_REPO" ||  "$SKIP_SDK_PUBLISH_TO_MAVEN_REPO" == "false" ]]
then
	if [ -z $TRUSTED_BUILD ]
	then
		echo "Cannot generate doc in an untrusted build, skipping"
	else
		if ! ./scripts/publish-sdk.bash
		then
			exit 1
		fi
	fi
else
	echo "-- Skipping SDK publishing."
fi

# Build and upload Olympus to Test Fairy
if [ "$CURRENT_BRANCH" == $RELEASE_BRANCH ] && [[ -z "$SKIP_TF_UPLOAD" || "$SKIP_TF_UPLOAD" == "false" ]]
then
	if [ -z $TRUSTED_BUILD ]
	then
		echo "Cannot generate doc in an untrusted build, skipping"
	else
		if ! ./scripts/upload-olympus-to-testfairy.bash
		then
			exit 1
		fi
	fi
else
	echo "-- Skipping Test Fairy upload."
fi

