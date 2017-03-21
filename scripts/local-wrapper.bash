#!/bin/bash
#
# Main script to be used for local builds (i.e. not Travis CI), that sets up the environment so that scripts designed for Travis CI can work locally,
# and we can do fast builds/testing/deployment even if Travis CI is not available (something that happens often, sadly)
#
# For local builds we need to have exported in your shell the following variables (for Travis they are setup via web/settings): 
# - GITHUB_OAUTH_TOKEN: token to be able to commit in GitHub repo from our scripts with no user intervention, for updating reference doc for example. I believe that this is different per repo (secret)
# - FILE_ENCRYPTION_PASSWORD: key used to symmetrically encrypt various sensitive files (like key files for signing in iOS) that need to be available inside the repo, and hence readable by public (secret) (deprecates ENTERPRISE_DISTRIBUTION_KEY_PASSWORD)
# - PRIVATE_KEY_PASSWORD: password to protect private keys (secret)
# - DEPLOY: i.e. true/false
# - BASE_VERSION: i.e. 1.0.0
# - VERSION_SUFFIX: i.e. beta.4.1
# - CUSTOM_KEYCHAIN_PASSWORD: password used for custom keychain we generate
# - TESTFAIRY_APP_TOKEN: Test Fairy App token, so that only CI builds send stats to TF
# - TESTFAIRY_API_KEY: Secret key for using TF API



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
else
	# Local build
	export CD_BRANCH="develop"
	export COMMIT_USERNAME="Antonis Tsakiridis"
	export DEPLOY="true"
fi

# Local build
#DEPLOY=true
#if [[ "$DEPLOY" == "true" ]]
#then
#fi

./scripts/main.bash
