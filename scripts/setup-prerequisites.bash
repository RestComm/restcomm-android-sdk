#!/bin/bash
#
# Setup prerequisites that are needed by all builds

# For now this is needed when building and publishing documentation because we need to push to upstream repo
git config credential.helper "store --file=.git/credentials" || exit 1
echo "https://${GITHUB_OAUTH_TOKEN}:@github.com" > .git/credentials 2>/dev/null || exit 1
git config user.name $COMMIT_USERNAME || exit 1
git config user.email "$COMMIT_AUTHOR_EMAIL" || exit 1

# SSH endpoint not needed any longer, since we 're using OAuth tokens with https, but let's leave it around in case we need it in the future
#export REPO=`git config remote.origin.url`
#export SSH_REPO=${REPO/https:\/\/github.com\//git@github.com:}

#echo "-- Will use ssh repo: $SSH_REPO"
#git remote -v

# Let's keep global gradle.properties decryption and installation only for Travis. Locally we have a working gradle.properties 
if [ ! -z "$TRAVIS" ]
then
	echo
	echo "== "
	echo "== Setting up Prerequisites"
	echo "== "
	echo

	mkdir -p ~/.gradle || exit 1

	echo "-- Decrypting and installing global gradle.properties"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} || exit 1
  	mv scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} || exit 1

	echo "-- Decrypting and installing gnupg resources for maven artifact signing + upload"
	# DEBUG
	#ls ~/.gnupg
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_SECRING}.enc -d -a -out scripts/configuration/${GPG_SECRING} || exit 1
  	mv scripts/configuration/${GPG_SECRING} ~/.gnupg/ || exit 1

	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_TRUSTDB}.enc -d -a -out scripts/configuration/${GPG_TRUSTDB} || exit 1
  	mv scripts/configuration/${GPG_TRUSTDB} ~/.gnupg/ || exit 1

	# Need keystore file to be able to sign the .apk. Let's keep debug.keystore decryption and installation only for Travis. Locally we have a working keystore that might be confusing to update.
	echo "-- Setting up signing for .apk"
	# We need the debug.keystore in order to be able to build a debug .apk
	echo "-- Decrypting signing keystore"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/keystore/${DEVELOPMENT_KEYSTORE}.enc -d -a -out scripts/certs/${DEVELOPMENT_KEYSTORE} || exit 1

	echo "-- Installing keystore"
	# Overwrite default keystore file only in travis
	cp scripts/certs/${DEVELOPMENT_KEYSTORE} ~/.android/${DEVELOPMENT_KEYSTORE} || exit 1
fi
