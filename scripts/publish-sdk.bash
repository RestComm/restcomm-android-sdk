#!/bin/bash
#
# Publish SDK to maven repository


# Let's keep global gradle.properties decryption and installation only for Travis. Locally we have a working gradle.properties 
if [ ! -z "$TRAVIS" ]
then
	echo "-- Decrypting and installing global gradle.properties"
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}.enc -d -a -out scripts/configuration/${GLOBAL_GRADLE_PROPERTIES}
  	cp scripts/configuration/${GLOBAL_GRADLE_PROPERTIES} ~/.gradle/${GLOBAL_GRADLE_PROPERTIES} 

	echo "-- Decrypting and installing gnupg resources"
	ls ~/.gnupg
	openssl aes-256-cbc -k "$FILE_ENCRYPTION_PASSWORD" -in scripts/configuration/${GPG_SECRING}.enc -d -a -out scripts/configuration/${GPG_SECRING}
  	cp scripts/configuration/${GPG_SECRING} ~/.gnupg/
fi

echo "-- Building SDK and uploading to maven repository"
cd restcomm.android.sdk && ./gradlew uploadArchives
cd ..
