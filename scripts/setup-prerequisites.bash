#!/bin/bash
#
# Setup prerequisites that are needed by all builds

# Let's keep global gradle.properties decryption and installation only for Travis. Locally we have a working gradle.properties 
if [ ! -z "$TRAVIS" ]
then
	mkdir -p ~/.gradle

	echo "-- Decrypting and installing global gradle.properties"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}
  	mv scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} 

	echo "-- Decrypting and installing gnupg resources"
	ls ~/.gnupg
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_SECRING}.enc -d -a -out scripts/configuration/${GPG_SECRING}
  	mv scripts/configuration/${GPG_SECRING} ~/.gnupg/

	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_TRUSTDB}.enc -d -a -out scripts/configuration/${GPG_TRUSTDB}
  	mv scripts/configuration/${GPG_TRUSTDB} ~/.gnupg/
fi

