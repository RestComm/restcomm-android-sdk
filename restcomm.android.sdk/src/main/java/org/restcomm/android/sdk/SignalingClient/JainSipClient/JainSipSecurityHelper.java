/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.SignalingClient.JainSipClient;

import android.content.Context;

import org.spongycastle.asn1.x509.BasicConstraints;
import org.spongycastle.asn1.x509.ExtendedKeyUsage;
import org.spongycastle.asn1.x509.GeneralName;
import org.spongycastle.asn1.x509.GeneralNames;
import org.spongycastle.asn1.x509.KeyPurposeId;
import org.spongycastle.asn1.x509.KeyUsage;
import org.spongycastle.asn1.x509.X509Extensions;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.x509.X509V3CertificateGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import javax.security.auth.x500.X500Principal;

public class JainSipSecurityHelper {
    private static final String TAG = "JainSipSecurityHelper";

    static {
        // IMPORTANT: make the SpongyCastle implementation take preference
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    /*
     * Create custom keystore, generate key and certificate and add them to it for use in encryption, etc, and in the end
     * also add to the keystore all the trusted certificates from the System Wide Android CA Store, so that we properly accept
     * legit server certificates
     *
     * @param context Android context
     * @param filename Filename to use for storing the keystore
     * @return HashMap containing keystore full path (key 'keystore-path') and keystore password (key 'keystore-password')
     */
    public static HashMap<String, String> generateKeystore(Context context, String filename)
    {
        HashMap<String, String> parameters = new HashMap<String, String>();
        try {
            SecureRandom random = new SecureRandom();
            // this yields 26 base32 characters
            parameters.put("keystore-password", new BigInteger(130, random).toString(32));

            // Create custom BKS store
            KeyStore ks = KeyStore.getInstance("BKS");
            ks.load(null);

            // Generate key pair using Elliptic Curve algorithm and Bouncy Castle provider
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = kpg.generateKeyPair();

            // Generate actual X509v3 certificate
            X509Certificate cert = generateCertificate(kp);
            X509Certificate[] certs = new X509Certificate[1];
            certs[0] = cert;

            // Add all the above in the keystore
            ks.setKeyEntry("restcomm-android-sdk", kp.getPrivate(), parameters.get("keystore-password").toCharArray(), certs);

            // Copy all trusted CA certs from System Wide keystore to our custom keystore, so that JAIN sip can properly
            // trust servers it talks to
            KeyStore CAks = KeyStore.getInstance("AndroidCAStore");
            CAks.load(null);
            Enumeration<String> aliases = CAks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                ks.setCertificateEntry(alias, CAks.getCertificate(alias));
            }

            // Save keystore in filesystem and retrieve path so that JAIN SIP can access it
            File keystoreFile = new File(context.getFilesDir(), filename);
            FileOutputStream outputStream = new FileOutputStream(keystoreFile);
            ks.store(outputStream, parameters.get("keystore-password").toCharArray());
            outputStream.close();
            parameters.put("keystore-path", keystoreFile.getAbsolutePath());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return parameters;
    }

    /*
    // Sets up TLS keystore and return a full path to it, usable by JAIN
    private String setupTls(Context context)
    {
        String filename = "restcomm.keystore";
        // Check if keystore file exists in internal storage and if not copy it from Assets folder. This
        // whole thing is needed because JAIN SIP needs a path to work with, and I haven't found a way
        // to reference assets using a path, so :(
        File keystoreFile = new File(context.getFilesDir(), filename);
        if (!keystoreFile.exists()) {
            BufferedReader reader = null;
            DataInputStream inputStream = null;
            try {
                //reader = new BufferedReader(new InputStreamReader(context.getAssets().open(filename)));
                inputStream = new DataInputStream(context.getAssets().open(filename));

                FileOutputStream outputStream = new FileOutputStream(keystoreFile);

                // do reading, usually loop until end of file reading
                String line;
                while (inputStream.available() > 0) {
                    byte b = inputStream.readByte();
                    outputStream.write(b);
                }
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return keystoreFile.getAbsolutePath();
    }
    */

    // generate X509 V3 Certificate
    public static X509Certificate generateCertificate(KeyPair pair)
            throws InvalidKeyException, NoSuchProviderException, SignatureException
    {
        // generate the certificate
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setIssuerDN(new X500Principal("CN=Restcomm Android SDK"));
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 50000));
        // TODO: using 1 day for now, need to increase that
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 86400000));
        certGen.setSubjectDN(new X500Principal("CN=Restcomm Android SDK"));
        certGen.setPublicKey(pair.getPublic());
        certGen.setSignatureAlgorithm("SHA1withECDSA");

        certGen.addExtension(X509Extensions.BasicConstraints, true, new BasicConstraints(false));
        certGen.addExtension(X509Extensions.KeyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        certGen.addExtension(X509Extensions.ExtendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        certGen.addExtension(X509Extensions.SubjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.rfc822Name, "android-sdk@cloud.restcomm.com")));

        // provider is Bouncy Castle
        return certGen.generateX509Certificate(pair.getPrivate(), "BC");
    }

    public static void setProperties(Properties properties, String keystorePath, String keystorePassword, Boolean disableCertVerification) {
        properties.setProperty("javax.net.ssl.keyStore", keystorePath);
        properties.setProperty("javax.net.ssl.trustStore", keystorePath);
        properties.setProperty("javax.net.ssl.keyStorePassword", keystorePassword );
        properties.setProperty("javax.net.ssl.keyStoreType", "bks" );
        properties.setProperty("android.gov.nist.javax.sip.ENABLED_CIPHER_SUITES", "TLS_RSA_WITH_AES_128_CBC_SHA SSL_RSA_WITH_3DES_EDE_CBC_SHA" );
        if (disableCertVerification != null && disableCertVerification) {
            properties.setProperty("android.gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "DisabledAll");
        }
    }

}
