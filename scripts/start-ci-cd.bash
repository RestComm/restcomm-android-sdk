#!/bin/bash
#
# Entry script to be used for setting up local + Travis builds
#
# Error Handling: The way this works to get proper error reporting from Travis
# (for critical errors that should stop the build) is that the outermost script
# must return non-zero status (i.e. typically exit 1). Notice that we avoided
# to use set -e, as it has numerous issues
#
# So the logic we use is that:
# - Each parent script needs to check the child's
# script return code and if it is non zero return non zero as well. 
# - The innermost script is responsible for reporting the reason of the error
# (any parents should just convey the status).
# - Only return details error code for the most important cases (otherwise we
# would clutter all code with checking each command's output)
# - For less important cases where we don't need details report but still need
# to stop the build we can just exit 1 if a command failed by using: $ cmd ||
# exit 1. The various logs we output before each action should help us identify
# the issue
# 
# Needed exported variables per build type:
#
# A. Trusted builds; initiated either from a commit of a team member, or direct PR created in upstream repo
#
# For local builds we need to have exported in our shell the following variables (for Travis they are setup via web/settings). Notice that variables prefixed with 'ORG_GRADLE_PROJECT' are automatically converted by gradle to 'gradle properties' and are meant to be used by gradle scripts. The rest are for the most part used by our own shell scripts
# - RELEASE_BRANCH: By default release branch is 'master', which means that by default builds in master get deployed in TF as well as Maven Central, but we can change that with this variable (optional) 
# - ORG_GRADLE_PROJECT_VERSION_NAME: base name, i.e. 1.0.0-BETA6
# - ORG_GRADLE_PROJECT_VERSION_CODE: version code, where we use Travis build number, i.e. 1
# - SKIP_TF_UPLOAD: skip Olympus upload to TestFairy for distribution, true/false (only needed if uploading to TF)
# - ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY: Secret key for using TestFairy API (only needed if uploading to TF)
# - ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE: Should we request from TestFairy to enable auto update for newly uploaded Olympus, true/false (only needed if uploading to TF)
# - TESTFAIRY_APP_TOKEN: Test Fairy App token, so that only CI builds send stats to TF
# - SKIP_OLYMPUS_UI_TESTS: skip espresso UI tests using Olympus, true/false
# - SKIP_INTEGRATION_TESTS: skip Integration Tests, true/false (not implemented yet)
# - SKIP_DOC_GENERATION: skip Reference Documentation generation, true/false
# - SKIP_SDK_PUBLISH_TO_MAVEN_REPO: Should SDK library artifact be signed and uploaded to Maven Central, true/false
# - GITHUB_OAUTH_TOKEN: token to be able to commit in GitHub repo from our scripts with no user intervention, for updating reference doc for example. I believe that this is different per repo (secret)  (only needed if generating doc)
# - FILE_ENCRYPTION_PASSWORD: key used to symmetrically encrypt various sensitive files (like key files for signing) that need to be available inside the repo, and hence readable by public (secret)
#
# Needed enrironment variables only valid for Local builds (i.e. not Travis), 
# - LOCAL_BUILD_NUMBER: build number if we are running locally -if running on Travis it's on TRAVIS_BUILD_NUMBER
# Because the SDK subproject doesn't have a local.properties file telling it where the SDK is, in contrast to Olympus project that has it
# - ANDROID_SDK, Android SDK location: i.e. /Users/antonis/Library/Android/sdk
#
# B. Untrusted builds; initiated from a PR created in forked repo
#
# For local builds we need to have exported in our shell the following variables (for Travis they are setup via web/settings). Notice that variables prefixed with 'ORG_GRADLE_PROJECT' are automatically converted by gradle to 'gradle properties' and are meant to be used by gradle scripts. The rest are for the most part used by our own shell scripts
# - ORG_GRADLE_PROJECT_VERSION_NAME: base name, i.e. 1.0.0-BETA6
# - ORG_GRADLE_PROJECT_VERSION_CODE: version code, where we use Travis build number, i.e. 1
# - SKIP_INTEGRATION_TESTS: skip Integration Tests, true/false
#
# Needed enrironment variables only valid for Local builds (i.e. not Travis), 
# - LOCAL_BUILD_NUMBER: build number if we are running locally -if running on Travis it's on TRAVIS_BUILD_NUMBER
# Because the SDK subproject doesn't have a local.properties file telling it where the SDK is, in contrast to Olympus project that has it
# - ANDROID_SDK, Android SDK location: i.e. /Users/antonis/Library/Android/sdk

# Common to local and travis builds
export COMMIT_AUTHOR_EMAIL="antonis.tsakiridis@telestax.com"
export APP_NAME="restcomm-olympus"
#export DEVELOPER_NAME="iPhone Distribution: Telestax, Inc."
#export DEVELOPMENT_TEAM="H9PG74NSQT"
# Keep the first seven chars from SHA1 as typically done
export COMMIT_SHA1=`git rev-parse HEAD | cut -c -7`
#export DEVELOPMENT_PROVISIONING_PROFILE_NAME="development"

#export APPLE_CERT="AppleWWDRCA.cer"
export DEVELOPMENT_KEYSTORE="debug.keystore"
export GLOBAL_GRADLE_PROPERTIES="gradle.properties"
export FIREBASE_ACCOUNT_KEY="firebase-service-account-key.json"
export GPG_SECRING="secring.gpg"
export RELEASE_KEYSTORE="android.jks"
export GPG_TRUSTDB="trustdb.gpg"
#export DEVELOPMENT_KEY="development-key.p12"
#export DISTRIBUTION_CERT="enterprise-distribution-cert.cer"
#export DISTRIBUTION_KEY="enterprise-distribution-key.p12"
#export DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME="profile-development-olympus"
#export DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME="profile-distribution-olympus"
#export CUSTOM_KEYCHAIN="ios-build.keychain"

# Global facilities for use by all other scripts
function is_git_repo_state_clean() {
	# Update the index
	#git update-index -q --ignore-submodules --refresh
	err=0

	# Disallow unstaged changes in the working tree
	if ! git diff-files --quiet --ignore-submodules --
	then
		echo >&2 "-- Error: you have unstaged changes."
		#git diff-files --name-status -r --ignore-submodules -- >&2
		err=1
	fi

	# Disallow uncommitted changes in the index
	if ! git diff-index --cached --quiet HEAD --ignore-submodules --
	then
		echo >&2 "-- Error: your staging area/index contains uncommitted changes."
		#git diff-index --cached --name-status -r --ignore-submodules HEAD -- >&2
		err=1
	fi

	# Disallow untracked changes 
	if [[ `git ls-files --other --exclude-standard --directory` ]]
	then
		echo >&2 "-- Error: you have untracked changes."
		#git diff-index --cached --name-status -r --ignore-submodules HEAD -- >&2
		err=1
	fi

	return $err;
}

# Export this function for use in other scripts as its pretty common
export -f is_git_repo_state_clean

# We need to differentiate between trusted and untrusted builds, so if TRUSTED_BUILD exists means the build is trusted (remember that is just for
# convenience, there are no guarantees -the real guarantee is the fact that encrypted variables aren't exposed in untrusted builds by Travis).
# Travis exports TRAVIS_SECURE_ENV_VARS when the build is trusted. For local builds lets export TRUSTED_BUILD ourselves for now and set to true
# and see how it goes 
if [[ "$TRAVIS_SECURE_ENV_VARS" == "true" ]]
then
	export TRUSTED_BUILD="true"
	echo "-- Trusted build"
else
	echo "-- Untrusted build"
fi

# By default release branch is 'master', which means that by default builds in master get deployed in TF as well as Maven Central
if [[ -z $RELEASE_BRANCH ]]
then
	export RELEASE_BRANCH="master"
fi

if [ ! -z "$TRAVIS" ]
then
	# Travis build
	export COMMIT_USERNAME="Travis CI"
	export ORG_GRADLE_PROJECT_BUILD_NUMBER=$TRAVIS_BUILD_NUMBER
	export CURRENT_BRANCH=$TRAVIS_BRANCH
else
	if [ -z "$LOCAL_BUILD_NUMBER" ]
	then
		echo "-- Error: LOCAL_BUILD_NUMBER is missing in local build"
		exit 1
	fi

	# Local build
	export COMMIT_USERNAME="Antonis Tsakiridis"
	export ORG_GRADLE_PROJECT_BUILD_NUMBER=$LOCAL_BUILD_NUMBER
	# Retrieve current branch name
	export CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`
fi

# Notice that even though ORG_GRADLE_PROJECT_VERSION_CODE and ORG_GRADLE_PROJECT_BUILD_NUMBER refer to the same thing (i.e. build number) for now we use the first 
# to denote the Android-specific versionCode, and the second to denote POM version when building SDK library artifact for upload to maven
export ORG_GRADLE_PROJECT_VERSION_CODE=$ORG_GRADLE_PROJECT_BUILD_NUMBER

echo "-- Validating environment"
if [ -z "$ORG_GRADLE_PROJECT_VERSION_CODE" ] || [ -z "$ORG_GRADLE_PROJECT_VERSION_NAME" ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_CODE and ORG_GRADLE_PROJECT_VERSION_NAME need to be set"
	exit 1
fi
#if [[ -z $ORG_GRADLE_PROJECT_VERSION_CODE && -z $TRAVIS ]]
#then
#	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_CODE environment variable missing"
#	exit 1
#fi
if [ -z $ORG_GRADLE_PROJECT_VERSION_NAME ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_NAME environment variable missing"
	exit 1
fi
if [ -z $CURRENT_BRANCH ]
then
	echo "-- Error: CURRENT_BRANCH environment variable missing"
	exit 1
fi

#if [ ! -z "$TRAVIS" ]
#then
#	# if this is a travis build, no need to do anything, just continue with main script
#	echo "-- This is Travis CI build, no need to local setup"
#	exit 0
#fi

# Print out interesting setup
echo "-- Executing in Travis: $TRAVIS"
echo "-- Build number: $ORG_GRADLE_PROJECT_BUILD_NUMBER"
echo "-- Current branch: $CURRENT_BRANCH"
echo "-- Current commit: $COMMIT_SHA1"

if ! ./scripts/main.bash
then
	echo
	echo "== "
	echo "== Build failed!"
	echo "== "
	echo

	exit 1
fi
