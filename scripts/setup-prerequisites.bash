#!/bin/bash
#
# Setup prerequisites that are needed by all builds

# Let's keep global gradle.properties decryption and installation only for Travis. Locally we have a working gradle.properties 
if [ ! -z "$TRAVIS" ]
then
	mkdir -p ~/.gradle || exit 1

	echo "-- Decrypting and installing global gradle.properties"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} || exit 1
  	mv scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} || exit 1

	echo "-- Decrypting and installing gnupg resources"
	# DEBUG
	#ls ~/.gnupg
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_SECRING}.enc -d -a -out scripts/configuration/${GPG_SECRING} || exit 1
  	mv scripts/configuration/${GPG_SECRING} ~/.gnupg/ || exit 1

	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_TRUSTDB}.enc -d -a -out scripts/configuration/${GPG_TRUSTDB} || exit 1
  	mv scripts/configuration/${GPG_TRUSTDB} ~/.gnupg/ || exit 1
fi

