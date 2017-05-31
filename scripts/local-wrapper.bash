#!/bin/bash
#
# Entry script to be used for setting up local + Travis builds
#
# For local builds we need to have exported in our shell the following variables (for Travis they are setup via web/settings). Notice that variables prefixed with 'ORG_GRADLE_PROJECT' are automatically converted by gradle to 'gradle properties' and are meant to be used by gradle scripts. The rest are for the most part used by our own shell scripts
# - ORG_GRADLE_PROJECT_VERSION_NAME: base name, i.e. 1.0.0-BETA6
# - ORG_GRADLE_PROJECT_VERSION_CODE: version code, where we use Travis build number, i.e. 1
# - ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY: Secret key for using TestFairy API
# - ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE: Should we request from TestFairy to enable auto update for newly uploaded Olympus, true/false
# - SKIP_TF_UPLOAD: skip Olympus upload to TestFairy for distribution, true/false
# - SKIP_INTEGRATION_TESTS: skip Integration Tests, true/false
# - SKIP_DOC_GENERATION: skip Reference Documentation generation, true/false
# - SKIP_OLYMPUS_BUILD: skip Olympus build, true/false
# - CD_BRANCH: branch on which to run CD on. Generally should be master, but we might change it to test a feature branch, etc
# - SKIP_SDK_PUBLISH_TO_MAVEN_REPO: Should SDK library artifact be signed and uploaded to Maven Central, true/false
# - GITHUB_OAUTH_TOKEN: token to be able to commit in GitHub repo from our scripts with no user intervention, for updating reference doc for example. I believe that this is different per repo (secret)
# - FILE_ENCRYPTION_PASSWORD: key used to symmetrically encrypt various sensitive files (like key files for signing) that need to be available inside the repo, and hence readable by public (secret)
# - DEPLOY: i.e. true/false
# - TESTFAIRY_APP_TOKEN: Test Fairy App token, so that only CI builds send stats to TF

# Only valid for Local builds, because the SDK subproject doesn't have a local.properties file telling it where the SDK is, in contrast to Olympus project that has it
# - ANDROID_SDK, Android SDK location: i.e. /Users/antonis/Library/Android/sdk
# - LOCAL_BUILD_NUMBER: build number if we are running locally -if running on Travis it's on TRAVIS_BUILD_NUMBER


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

echo "-- Validating environment"
if [ -z $ORG_GRADLE_PROJECT_VERSION_CODE ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_CODE environment variable missing"
	exit 1
fi
if [ -z $ORG_GRADLE_PROJECT_VERSION_NAME ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_VERSION_NAME environment variable missing"
	exit 1
fi
if [ -z $ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_TESTFAIRY_APIKEY environment variable missing"
	exit 1
fi
if [ -z $GITHUB_OAUTH_TOKEN ]
then
	echo "-- Error: GITHUB_OAUTH_TOKEN environment variable missing"
	exit 1
fi
if [ -z $FILE_ENCRYPTION_PASSWORD ]
then
	echo "-- Error: FILE_ENCRYPTION_PASSWORD environment variable missing"
	exit 1
fi
if [ -z $TESTFAIRY_APP_TOKEN ]
then
	echo "-- Error: TESTFAIRY_APP_TOKEN environment variable missing"
	exit 1
fi
if [ -z $CD_BRANCH ]
then
	echo "-- Error: CD_BRANCH environment variable missing"
	exit 1
fi
if [ -z $ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE ]
then
	echo "-- Error: ORG_GRADLE_PROJECT_TESTFAIRY_AUTOUPDATE environment variable missing"
	exit 1
fi

#if [ ! -z "$TRAVIS" ]
#then
#	# if this is a travis build, no need to do anything, just continue with main script
#	echo "-- This is Travis CI build, no need to local setup"
#	exit 0
#fi

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
export GPG_TRUSTDB="trustdb.gpg"
#export DEVELOPMENT_KEY="development-key.p12"
#export DISTRIBUTION_CERT="enterprise-distribution-cert.cer"
#export DISTRIBUTION_KEY="enterprise-distribution-key.p12"
#export DEVELOPMENT_PROVISIONING_PROFILE_OLYMPUS_NAME="profile-development-olympus"
#export DISTRIBUTION_PROVISIONING_PROFILE_OLYMPUS_NAME="profile-distribution-olympus"
#export CUSTOM_KEYCHAIN="ios-build.keychain"

if [ ! -z "$TRAVIS" ]
then
	# Travis build
	export COMMIT_USERNAME="Travis CI"
	export ORG_GRADLE_PROJECT_BUILD_NUMBER=$TRAVIS_BUILD_NUMBER
else
	# Local build
	#export CD_BRANCH="develop"
	export COMMIT_USERNAME="Antonis Tsakiridis"
	#export DEPLOY="true"
	export ORG_GRADLE_PROJECT_BUILD_NUMBER=$LOCAL_BUILD_NUMBER
fi

# Print out interesting setup
echo "-- Executing in Travis: $TRAVIS"
echo "-- Build number: $ORG_GRADLE_PROJECT_BUILD_NUMBER"
echo "-- Current commit: $COMMIT_SHA1"

# Local build
#DEPLOY=true
#if [[ "$DEPLOY" == "true" ]]
#then
#fi

if ! ./scripts/main.bash
then
	exit 1
fi
