#!/bin/bash
#
# Main script that will drive CI/CD actions, depending on type of commit.

echo "-- Processing main script."

# Run integration tests in simulator - TODO: take this out to a separate script
echo "-- Running Integration Tests on simulator."
if [ -z "$SKIP_INTEGRATION_TESTS" ] || [[ "$SKIP_INTEGRATION_TESTS" == "false" ]]
then
	# TODO: this should become a single line both for local and travis builds
	if [ ! -z "$TRAVIS" ]
	then
		#set -o pipefail && travis_retry xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE,OS=10.0' | xcpretty
		#xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE'
		echo "TODO: add travis IT"
	else
		# For local builds don't specify iOS version, to make it more flexible
		#xcodebuild test -workspace Test-App/Sample.xcworkspace -scheme Sample -destination 'platform=iOS Simulator,name=iPhone SE' | xcpretty
		echo "TODO: add local IT"
	fi
else
	echo "-- Skipping Integration Tests."
fi

if [ ! -z "$TRAVIS" ]
then
	# This is a travis build
	if [[ "$TRAVIS_PULL_REQUEST" == "true" ]]; then
		echo "-- This is a pull request, bailing out."
		exit 0
	fi

	# CD_BRANCH is the brach we are passing from the travis CI settings and shows which branch CI should deploy from
	if [[ "$TRAVIS_BRANCH" != "$CD_BRANCH" ]]; then
		echo "-- Testing on a branch other than $CD_BRANCH, bailing out."
		exit 0
	fi
else
	# This is a local build
	if [[ "$DEPLOY" != "true" ]]
	then
		echo "-- This is a local build and DEPLOY env variable is not true, bailing out."
		exit 0
	fi
fi

git config credential.helper "store --file=.git/credentials"; echo "https://${GITHUB_OAUTH_TOKEN}:@github.com" > .git/credentials 2>/dev/null
git config user.name $COMMIT_USERNAME
git config user.email "$COMMIT_AUTHOR_EMAIL"

# SSH endpoint not needed any longer, since we 're using OAuth tokens with https, but let's leave it around in case we need it in the future
#export REPO=`git config remote.origin.url`
#export SSH_REPO=${REPO/https:\/\/github.com\//git@github.com:}

#echo "-- Will use ssh repo: $SSH_REPO"
#git remote -v


# Update reference documentation
if [ -z "$SKIP_DOC_GENERATION" ] || [[ "$SKIP_DOC_GENERATION" == "false" ]]
then
	./scripts/update-doc.bash
else
	echo "-- Skipping Documentation Generation."
fi

# Build SDK and publish to maven repo
if [ -z "$SKIP_SDK_PUBLISH_TO_MAVEN_REPO" ] || [[ "$SKIP_SDK_PUBLISH_TO_MAVEN_REPO" == "false" ]]
then
	./scripts/publish-sdk.bash
else
	echo "-- Skipping SDK publishing."
fi

# Build and deploy Olympus
if [ -z "$SKIP_OLYMPUS_BUILD" ]  || [[ "$SKIP_OLYMPUS_BUILD" == "false" ]]
then
	./scripts/build-olympus.bash
else
	echo "-- Skipping Olympus build."
fi

# Update the pod
#- pod lib lint
